-- 2026-10-19: 원격 푸시(FCM) 기기·동의 이력·발송 기록·배치 실행 기록
--
-- 계획서: .claude/plans/native-push.md 5장(서버)
--
-- 설계 요약
-- - push_devices: 알림을 켠 기기만 등록된다(앱이 FCM auto-init 을 끄고 알림을 켤 때만
--   토큰을 받는다). pk 는 푸시 전용 pushInstallId — 학습 installId 는 계정 변경 시
--   재생성되므로 쓰지 않는다. 익명 계정도 auth.users 에 있으므로 FK 가 성립한다.
-- - 광고 동의(marketing_opt_in)는 POST /devices 로 바꿀 수 없고 동의 RPC 로만 바뀐다.
--   서버 기록 ack 를 받은 뒤에만 앱이 promo 토픽을 구독한다(정보통신망법 제50조 ⑦ 처리 결과 통지,
--   시행령 제62조의3 2년 확인의 근거 자료).
-- - push_consent_events: 추가 전용 이력. 수정은 트리거로 막는다. 삭제는 계정 삭제
--   (gugu_purge_owner_data, auth.users cascade)에서만 일어난다 — 미결정 8 "계정 삭제 시 동의 이력 삭제".
-- - 광고 동의가 켜진 기기 행이 어떤 이유로든 사라지면(해제·소유자 변경·토큰 교체·토큰 정리)
--   granted=false 이력을 함께 남긴다. 동의 상태의 마지막 사실을 이력만으로 재구성할 수 있게 한다.
-- - push_sends: dedupe_key 로 같은 알림을 두 번 보내지 않는다(배치 매시 실행 멱등).
--   claimed 인 채로 끝난 행(발송 중 프로세스 종료)은 다시 잡지 않는다 — 일부 기기가 이미
--   받았을 수 있으므로 중복보다 누락을 택한다. failed(한 건도 못 보냄)만 재시도한다.
-- - 야간(21~08시) 광고 동의 컬럼·토픽은 만들지 않는다 — 미결정 1 "야간 광고 보내지 않음".
--
-- 보안: 네 테이블 모두 RLS 를 켜고 정책을 두지 않으며 anon/authenticated 권한을 회수한다.
-- 쓰기와 조회는 service_role 전용 security definer RPC 로만 한다(search_path '' 고정).
--
-- 적용 순서: 이 파일 → 20261019_purge_owner_data_push.sql
-- 되돌리기: drop function ... ; drop table public.push_job_runs, public.push_sends,
--           public.push_consent_events, public.push_devices; (운영 데이터가 있으면 먼저 백업)

-- ---------------------------------------------------------------------------
-- 테이블
-- ---------------------------------------------------------------------------

create table if not exists public.push_devices (
    push_install_id uuid primary key,
    user_id uuid not null references auth.users (id) on delete cascade,
    fcm_token text not null unique
        check (char_length(fcm_token) between 16 and 4096),
    platform text not null check (platform in ('ios', 'android')),
    locale text check (locale is null or char_length(locale) <= 35),
    time_zone text check (time_zone is null or char_length(time_zone) <= 64),
    app_version text not null check (char_length(app_version) between 1 and 32),
    service_opt_in boolean not null default false,
    learning_opt_in boolean not null default false,
    local_reminder_enabled boolean not null default false,
    marketing_opt_in boolean not null default false,
    marketing_opt_in_at timestamptz,
    marketing_reconfirmed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    -- 동의가 켜져 있으면 동의 시각이 반드시 있다(2년 재확인 기준일)
    constraint push_devices_marketing_at_chk
        check (not marketing_opt_in or marketing_opt_in_at is not null)
);

create index if not exists push_devices_user_id_idx
    on public.push_devices (user_id);
create index if not exists push_devices_last_seen_at_idx
    on public.push_devices (last_seen_at);
create index if not exists push_devices_marketing_idx
    on public.push_devices (coalesce(marketing_reconfirmed_at, marketing_opt_in_at))
    where marketing_opt_in;

create table if not exists public.push_consent_events (
    id bigint generated always as identity primary key,
    user_id uuid not null references auth.users (id) on delete cascade,
    -- 기기 행이 지워진 뒤에도 이력은 남아야 하므로 FK 를 두지 않는다
    push_install_id uuid not null,
    kind text not null check (kind = 'marketing'),
    granted boolean not null,
    source text not null check (source in (
        'settings',             -- 설정 화면 토글
        'reconfirm',            -- 2년 확인 알림을 보고 다시 동의
        'reconfirm_notice',     -- 2년 확인 알림 발송(무응답이면 동의 유지)
        'device_unregistered',  -- 기기 등록 해제(알림 모두 끔·로그아웃)
        'device_reassigned',    -- 같은 기기가 다른 계정으로 등록됨
        'token_replaced',       -- 같은 FCM 토큰이 다른 설치로 등록됨
        'token_pruned',         -- 무효 토큰·장기 미사용 정리
        'device_evicted'        -- 계정당 기기 수 상한 초과로 가장 오래된 기기 정리
    )),
    app_version text check (app_version is null or char_length(app_version) <= 32),
    recorded_at timestamptz not null default clock_timestamp()
);

create index if not exists push_consent_events_user_id_idx
    on public.push_consent_events (user_id, recorded_at);

create table if not exists public.push_sends (
    dedupe_key text primary key check (char_length(dedupe_key) between 1 and 200),
    kind text not null check (char_length(kind) between 1 and 64),
    -- send-token(테스트 기기) 발송은 계정이 없다
    user_id uuid references auth.users (id) on delete cascade,
    status text not null check (status in ('claimed', 'sent', 'partial', 'failed')),
    target_count int not null default 0 check (target_count >= 0),
    success_count int not null default 0 check (success_count >= 0),
    failure_count int not null default 0 check (failure_count >= 0),
    pruned_count int not null default 0 check (pruned_count >= 0),
    error_code text check (error_code is null or char_length(error_code) <= 64),
    claimed_at timestamptz not null default now(),
    finished_at timestamptz
);

create index if not exists push_sends_user_id_idx
    on public.push_sends (user_id) where user_id is not null;

create table if not exists public.push_job_runs (
    id bigint generated always as identity primary key,
    job text not null check (job in ('weekly_report', 'marketing_reconfirm', 'prune')),
    status text not null check (status in ('ok', 'skipped', 'dry_run', 'failed')),
    started_at timestamptz not null,
    finished_at timestamptz not null default now(),
    -- 건수·건너뛴 사유만 담는다. 이메일·토큰 원문을 넣지 않는다
    detail jsonb not null default '{}'::jsonb
);

create index if not exists push_job_runs_finished_at_idx
    on public.push_job_runs (finished_at desc);

alter table public.push_devices enable row level security;
alter table public.push_consent_events enable row level security;
alter table public.push_sends enable row level security;
alter table public.push_job_runs enable row level security;

revoke all on table public.push_devices from public, anon, authenticated;
revoke all on table public.push_consent_events from public, anon, authenticated;
revoke all on table public.push_sends from public, anon, authenticated;
revoke all on table public.push_job_runs from public, anon, authenticated;

-- 동의 이력은 추가 전용 — 수정을 막는다(삭제는 계정 삭제 경로에서만 허용)
create or replace function public.gugu_push_consent_events_no_update()
returns trigger
language plpgsql
set search_path to ''
as $function$
begin
    raise exception using errcode = '42501', message = 'CONSENT_EVENTS_APPEND_ONLY';
end;
$function$;

revoke all on function public.gugu_push_consent_events_no_update() from public, anon, authenticated;

drop trigger if exists push_consent_events_no_update on public.push_consent_events;
create trigger push_consent_events_no_update
    before update on public.push_consent_events
    for each row execute function public.gugu_push_consent_events_no_update();

-- ---------------------------------------------------------------------------
-- 기기 등록 — 같은 설치 갱신·소유자 변경·같은 토큰 다른 행 정리·기기 10개 제한
-- ---------------------------------------------------------------------------

create or replace function public.gugu_push_register_device(
    p_user_id uuid,
    p_push_install_id uuid,
    p_fcm_token text,
    p_platform text,
    p_locale text,
    p_time_zone text,
    p_app_version text,
    p_service_opt_in boolean,
    p_learning_opt_in boolean,
    p_local_reminder_enabled boolean
)
returns jsonb
language plpgsql
security definer
set search_path to ''
as $function$
declare
    v_max_devices constant int := 10;
    v_existing_owner uuid;
    v_replaced int := 0;
    v_reassigned int := 0;
    v_evicted int := 0;
    v_row public.push_devices%rowtype;
begin
    if p_user_id is null or p_push_install_id is null then
        raise exception using errcode = '22023', message = 'OWNER_REQUIRED';
    end if;
    if p_fcm_token is null or p_platform is null or p_app_version is null
       or p_service_opt_in is null or p_learning_opt_in is null
       or p_local_reminder_enabled is null then
        raise exception using errcode = '22023', message = 'INVALID_DEVICE_PAYLOAD';
    end if;

    -- 같은 설치를 두 요청이 동시에 등록하는 경합을 직렬화한다
    perform pg_advisory_xact_lock(hashtextextended(p_push_install_id::text, 0));

    -- 같은 FCM 토큰이 다른 설치로 남아 있으면 지운다 — 토큰은 지금 이 설치의 것이다
    insert into public.push_consent_events (user_id, push_install_id, kind, granted, source, app_version)
    select d.user_id, d.push_install_id, 'marketing', false, 'token_replaced', p_app_version
    from public.push_devices d
    where d.fcm_token = p_fcm_token
      and d.push_install_id <> p_push_install_id
      and d.marketing_opt_in;

    delete from public.push_devices
    where fcm_token = p_fcm_token
      and push_install_id <> p_push_install_id;
    get diagnostics v_replaced = row_count;

    -- 같은 설치가 다른 계정으로 등록되면(로그아웃 전 DELETE 실패 등) 이전 계정의 행을
    -- 지우고 새로 만든다. 광고 동의는 사람이 한 것이므로 새 계정에 넘기지 않는다.
    select user_id into v_existing_owner
    from public.push_devices
    where push_install_id = p_push_install_id
    for update;

    if v_existing_owner is not null and v_existing_owner <> p_user_id then
        insert into public.push_consent_events (user_id, push_install_id, kind, granted, source, app_version)
        select d.user_id, d.push_install_id, 'marketing', false, 'device_reassigned', p_app_version
        from public.push_devices d
        where d.push_install_id = p_push_install_id
          and d.marketing_opt_in;

        delete from public.push_devices
        where push_install_id = p_push_install_id;
        get diagnostics v_reassigned = row_count;
        v_existing_owner := null;
    end if;

    -- 새 기기라면 계정당 기기 수 상한을 지킨다 — 가장 오래 안 보인 기기부터 정리한다.
    -- 거부하면 오래된 기기가 남은 계정에서 새 기기 알림이 영영 켜지지 않는다.
    if v_existing_owner is null then
        perform pg_advisory_xact_lock(hashtextextended(p_user_id::text, 0));

        insert into public.push_consent_events (user_id, push_install_id, kind, granted, source, app_version)
        select d.user_id, d.push_install_id, 'marketing', false, 'device_evicted', p_app_version
        from public.push_devices d
        where d.push_install_id in (
            select push_install_id
            from public.push_devices
            where user_id = p_user_id
            order by last_seen_at desc, push_install_id
            offset v_max_devices - 1
        )
          and d.marketing_opt_in;

        delete from public.push_devices
        where push_install_id in (
            select push_install_id
            from public.push_devices
            where user_id = p_user_id
            order by last_seen_at desc, push_install_id
            offset v_max_devices - 1
        );
        get diagnostics v_evicted = row_count;
    end if;

    insert into public.push_devices (
        push_install_id,
        user_id,
        fcm_token,
        platform,
        locale,
        time_zone,
        app_version,
        service_opt_in,
        learning_opt_in,
        local_reminder_enabled
    ) values (
        p_push_install_id,
        p_user_id,
        p_fcm_token,
        p_platform,
        p_locale,
        p_time_zone,
        p_app_version,
        p_service_opt_in,
        p_learning_opt_in,
        p_local_reminder_enabled
    )
    on conflict (push_install_id) do update
    set fcm_token = excluded.fcm_token,
        platform = excluded.platform,
        locale = excluded.locale,
        time_zone = excluded.time_zone,
        app_version = excluded.app_version,
        service_opt_in = excluded.service_opt_in,
        learning_opt_in = excluded.learning_opt_in,
        local_reminder_enabled = excluded.local_reminder_enabled,
        updated_at = now(),
        last_seen_at = now()
    where public.push_devices.user_id = excluded.user_id
    returning * into v_row;

    if v_row.push_install_id is null then
        -- 위에서 소유자를 정리했으므로 도달하면 동시성 계약이 깨진 것이다
        raise exception using errcode = '40001', message = 'DEVICE_OWNERSHIP_RACE';
    end if;

    -- 앱은 이 응답(서버 기준 동의 상태)으로 promo 토픽 구독을 맞춘다
    return jsonb_build_object(
        'pushInstallId', v_row.push_install_id,
        'serviceOptIn', v_row.service_opt_in,
        'learningOptIn', v_row.learning_opt_in,
        'localReminderEnabled', v_row.local_reminder_enabled,
        'marketingOptIn', v_row.marketing_opt_in,
        'marketingOptInAt', v_row.marketing_opt_in_at,
        'marketingReconfirmedAt', v_row.marketing_reconfirmed_at,
        'updatedAt', v_row.updated_at,
        'replacedTokenRows', v_replaced,
        'reassignedRows', v_reassigned,
        'evictedDevices', v_evicted
    );
end;
$function$;

revoke all on function public.gugu_push_register_device(
    uuid, uuid, text, text, text, text, text, boolean, boolean, boolean
) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 광고 동의 기록 — 소유자 확인 + 플래그·시각 갱신 + 이력 추가를 한 트랜잭션으로
-- ---------------------------------------------------------------------------

create or replace function public.gugu_push_set_consent(
    p_user_id uuid,
    p_push_install_id uuid,
    p_kind text,
    p_granted boolean,
    p_source text,
    p_app_version text
)
returns jsonb
language plpgsql
security definer
set search_path to ''
as $function$
declare
    v_now timestamptz := clock_timestamp();
    v_current boolean;
    v_recorded_at timestamptz;
begin
    if p_user_id is null or p_push_install_id is null or p_granted is null then
        raise exception using errcode = '22023', message = 'INVALID_CONSENT_PAYLOAD';
    end if;
    if p_kind is distinct from 'marketing' then
        raise exception using errcode = '22023', message = 'UNSUPPORTED_CONSENT_KIND';
    end if;
    if p_source is null or p_source not in ('settings', 'reconfirm') then
        raise exception using errcode = '22023', message = 'UNSUPPORTED_CONSENT_SOURCE';
    end if;

    select marketing_opt_in into v_current
    from public.push_devices
    where push_install_id = p_push_install_id
      and user_id = p_user_id
    for update;

    if not found then
        -- 남의 기기와 없는 기기를 구분하지 않는다(존재 여부 노출 방지)
        return jsonb_build_object('found', false);
    end if;

    if p_granted and v_current then
        -- 이미 동의한 상태에서 다시 동의 = 2년 확인에 대한 유지 의사 표시
        update public.push_devices
        set marketing_reconfirmed_at = v_now,
            updated_at = v_now
        where push_install_id = p_push_install_id;
    elsif p_granted then
        update public.push_devices
        set marketing_opt_in = true,
            marketing_opt_in_at = v_now,
            marketing_reconfirmed_at = null,
            updated_at = v_now
        where push_install_id = p_push_install_id;
    else
        update public.push_devices
        set marketing_opt_in = false,
            marketing_opt_in_at = null,
            marketing_reconfirmed_at = null,
            updated_at = v_now
        where push_install_id = p_push_install_id;
    end if;

    insert into public.push_consent_events (
        user_id, push_install_id, kind, granted, source, app_version, recorded_at
    ) values (
        p_user_id, p_push_install_id, p_kind, p_granted, p_source, p_app_version, v_now
    )
    returning recorded_at into v_recorded_at;

    return jsonb_build_object(
        'found', true,
        'kind', p_kind,
        'granted', p_granted,
        'recordedAt', v_recorded_at
    );
end;
$function$;

revoke all on function public.gugu_push_set_consent(
    uuid, uuid, text, boolean, text, text
) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 기기 등록 해제 — 멱등. 남의 기기는 forbidden(API 가 404 로 응답)
-- ---------------------------------------------------------------------------

create or replace function public.gugu_push_unregister_device(
    p_user_id uuid,
    p_push_install_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path to ''
as $function$
declare
    v_owner uuid;
    v_marketing boolean;
begin
    if p_user_id is null or p_push_install_id is null then
        raise exception using errcode = '22023', message = 'OWNER_REQUIRED';
    end if;

    select user_id, marketing_opt_in into v_owner, v_marketing
    from public.push_devices
    where push_install_id = p_push_install_id
    for update;

    if not found then
        return jsonb_build_object('status', 'absent');
    end if;
    if v_owner <> p_user_id then
        return jsonb_build_object('status', 'forbidden');
    end if;

    if v_marketing then
        insert into public.push_consent_events (user_id, push_install_id, kind, granted, source)
        values (p_user_id, p_push_install_id, 'marketing', false, 'device_unregistered');
    end if;

    delete from public.push_devices
    where push_install_id = p_push_install_id;

    return jsonb_build_object('status', 'deleted');
end;
$function$;

revoke all on function public.gugu_push_unregister_device(uuid, uuid) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 토큰 정리 — FCM 이 무효라고 답한 토큰 + 오래 안 보인 기기(배치)
-- ---------------------------------------------------------------------------

create or replace function public.gugu_push_prune_tokens(
    p_tokens text[] default null,
    p_last_seen_before timestamptz default null,
    p_dry_run boolean default false
)
returns jsonb
language plpgsql
security definer
set search_path to ''
as $function$
declare
    v_by_token int := 0;
    v_stale int := 0;
begin
    if p_tokens is null and p_last_seen_before is null then
        raise exception using errcode = '22023', message = 'PRUNE_TARGET_REQUIRED';
    end if;
    if p_tokens is not null and cardinality(p_tokens) > 1000 then
        raise exception using errcode = '22023', message = 'TOO_MANY_TOKENS';
    end if;
    if p_last_seen_before is not null and p_last_seen_before > now() - interval '30 days' then
        -- 실수로 최근 기기까지 지우는 것을 막는다
        raise exception using errcode = '22023', message = 'STALE_CUTOFF_TOO_RECENT';
    end if;

    if coalesce(p_dry_run, false) then
        select count(*) into v_by_token
        from public.push_devices
        where p_tokens is not null and fcm_token = any (p_tokens);

        select count(*) into v_stale
        from public.push_devices
        where p_last_seen_before is not null
          and last_seen_at < p_last_seen_before
          and not (p_tokens is not null and fcm_token = any (p_tokens));

        return jsonb_build_object('prunedByToken', v_by_token, 'prunedStale', v_stale, 'dryRun', true);
    end if;

    if p_tokens is not null then
        insert into public.push_consent_events (user_id, push_install_id, kind, granted, source)
        select d.user_id, d.push_install_id, 'marketing', false, 'token_pruned'
        from public.push_devices d
        where d.fcm_token = any (p_tokens)
          and d.marketing_opt_in;

        delete from public.push_devices
        where fcm_token = any (p_tokens);
        get diagnostics v_by_token = row_count;
    end if;

    if p_last_seen_before is not null then
        insert into public.push_consent_events (user_id, push_install_id, kind, granted, source)
        select d.user_id, d.push_install_id, 'marketing', false, 'token_pruned'
        from public.push_devices d
        where d.last_seen_at < p_last_seen_before
          and d.marketing_opt_in;

        delete from public.push_devices
        where last_seen_at < p_last_seen_before;
        get diagnostics v_stale = row_count;
    end if;

    return jsonb_build_object('prunedByToken', v_by_token, 'prunedStale', v_stale, 'dryRun', false);
end;
$function$;

revoke all on function public.gugu_push_prune_tokens(text[], timestamptz, boolean)
    from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 운영자 개별 발송 대상 — 이메일 계정의 전체 기기
-- ---------------------------------------------------------------------------

create or replace function public.gugu_push_devices_for_email(p_email text)
returns jsonb
language plpgsql
security definer
set search_path to ''
as $function$
declare
    v_user_ids uuid[];
begin
    if p_email is null or char_length(btrim(p_email)) < 3 then
        raise exception using errcode = '22023', message = 'EMAIL_REQUIRED';
    end if;

    select array_agg(u.id) into v_user_ids
    from auth.users u
    where lower(u.email) = lower(btrim(p_email))
      and u.deleted_at is null;

    if v_user_ids is null then
        return jsonb_build_object('found', false);
    end if;
    if cardinality(v_user_ids) > 1 then
        -- 어느 계정인지 모르는 채로 보내지 않는다
        raise exception using errcode = '21000', message = 'EMAIL_NOT_UNIQUE';
    end if;

    return jsonb_build_object(
        'found', true,
        'userId', v_user_ids[1],
        'devices', coalesce((
            select jsonb_agg(jsonb_build_object(
                'pushInstallId', d.push_install_id,
                'fcmToken', d.fcm_token,
                'platform', d.platform
            ) order by d.last_seen_at desc)
            from public.push_devices d
            where d.user_id = v_user_ids[1]
        ), '[]'::jsonb)
    );
end;
$function$;

revoke all on function public.gugu_push_devices_for_email(text) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 발송 중복 방지 — 선점(claim)과 완료 기록
-- ---------------------------------------------------------------------------

create or replace function public.gugu_push_claim_send(
    p_dedupe_key text,
    p_kind text,
    p_user_id uuid default null
)
returns jsonb
language plpgsql
security definer
set search_path to ''
as $function$
declare
    v_status text;
begin
    if p_dedupe_key is null or p_kind is null then
        raise exception using errcode = '22023', message = 'DEDUPE_KEY_REQUIRED';
    end if;

    insert into public.push_sends (dedupe_key, kind, user_id, status)
    values (p_dedupe_key, p_kind, p_user_id, 'claimed')
    on conflict (dedupe_key) do nothing;

    if found then
        return jsonb_build_object('claimed', true, 'status', 'claimed');
    end if;

    -- 한 건도 보내지 못한 발송만 다시 잡는다
    update public.push_sends
    set status = 'claimed',
        claimed_at = now(),
        finished_at = null,
        error_code = null,
        target_count = 0,
        success_count = 0,
        failure_count = 0,
        pruned_count = 0
    where dedupe_key = p_dedupe_key
      and status = 'failed';

    if found then
        return jsonb_build_object('claimed', true, 'status', 'claimed');
    end if;

    select status into v_status from public.push_sends where dedupe_key = p_dedupe_key;
    return jsonb_build_object('claimed', false, 'status', v_status);
end;
$function$;

revoke all on function public.gugu_push_claim_send(text, text, uuid) from public, anon, authenticated;

create or replace function public.gugu_push_finish_send(
    p_dedupe_key text,
    p_status text,
    p_target_count int,
    p_success_count int,
    p_failure_count int,
    p_pruned_count int,
    p_error_code text default null
)
returns jsonb
language plpgsql
security definer
set search_path to ''
as $function$
begin
    if p_status is null or p_status not in ('sent', 'partial', 'failed') then
        raise exception using errcode = '22023', message = 'INVALID_SEND_STATUS';
    end if;

    update public.push_sends
    set status = p_status,
        target_count = p_target_count,
        success_count = p_success_count,
        failure_count = p_failure_count,
        pruned_count = p_pruned_count,
        error_code = p_error_code,
        finished_at = now()
    where dedupe_key = p_dedupe_key
      and status = 'claimed';

    if not found then
        raise exception using errcode = 'P0002', message = 'SEND_NOT_CLAIMED';
    end if;
    return jsonb_build_object('dedupeKey', p_dedupe_key, 'status', p_status);
end;
$function$;

revoke all on function public.gugu_push_finish_send(text, text, int, int, int, int, text)
    from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 주간 리포트 후보 — 보호자 검증 + 해당 주 학습 활동 + learning_opt_in 기기
-- 이용권(구매 원장은 MariaDB)은 API 쪽 entitlements 로 따로 확인한다.
-- ---------------------------------------------------------------------------

create or replace function public.gugu_push_weekly_candidates(
    p_from timestamptz,
    p_to timestamptz
)
returns jsonb
language plpgsql
stable
security definer
set search_path to ''
as $function$
begin
    if p_from is null or p_to is null or p_from >= p_to or p_to - p_from > interval '8 days' then
        raise exception using errcode = '22023', message = 'INVALID_WEEK_RANGE';
    end if;

    return jsonb_build_object('candidates', coalesce((
        select jsonb_agg(jsonb_build_object(
            'userId', u.id,
            'devices', (
                select jsonb_agg(jsonb_build_object(
                    'pushInstallId', d.push_install_id,
                    'fcmToken', d.fcm_token,
                    'platform', d.platform
                ) order by d.last_seen_at desc)
                from public.push_devices d
                where d.user_id = u.id
                  and d.learning_opt_in
            )
        ) order by u.id)
        from auth.users u
        where u.deleted_at is null
          -- learning.py require_learning_upload_permission 과 같은 보호자 조건
          and u.raw_app_meta_data ->> 'account_role' = 'guardian'
          and u.raw_app_meta_data -> 'age_verified' = 'true'::jsonb
          and exists (
              select 1
              from public.push_devices d
              where d.user_id = u.id
                and d.learning_opt_in
          )
          and exists (
              select 1
              from public.answer_events e
              join public.learners l
                on l.id = e.learner_id
               and l.owner_user_id = u.id
               and l.status = 'active'
              where e.owner_user_id = u.id
                and e.occurred_at >= p_from
                and e.occurred_at < p_to
          )
    ), '[]'::jsonb));
end;
$function$;

revoke all on function public.gugu_push_weekly_candidates(timestamptz, timestamptz)
    from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 광고 수신동의 2년 확인 — 후보 조회와 발송 기록
-- 기준일 = coalesce(마지막 확인, 동의 시각). 기준일 + 2년 - 14일이 지나면 대상이다.
-- 마감 시각 계산은 API(push_jobs.reconfirm_cutoff)가 달력 기준으로 한다.
-- ---------------------------------------------------------------------------

create or replace function public.gugu_push_reconfirm_candidates(
    p_basis_before timestamptz,
    p_limit int default 500
)
returns jsonb
language plpgsql
stable
security definer
set search_path to ''
as $function$
begin
    if p_basis_before is null or p_basis_before > now() - interval '700 days' then
        raise exception using errcode = '22023', message = 'INVALID_RECONFIRM_CUTOFF';
    end if;
    if p_limit is null or p_limit < 1 or p_limit > 1000 then
        raise exception using errcode = '22023', message = 'INVALID_LIMIT';
    end if;

    return jsonb_build_object('candidates', coalesce((
        select jsonb_agg(jsonb_build_object(
            'pushInstallId', c.push_install_id,
            'userId', c.user_id,
            'fcmToken', c.fcm_token,
            'platform', c.platform,
            'marketingOptInAt', c.marketing_opt_in_at,
            'basisAt', c.basis_at
        ) order by c.basis_at)
        from (
            select d.push_install_id,
                   d.user_id,
                   d.fcm_token,
                   d.platform,
                   d.marketing_opt_in_at,
                   coalesce(d.marketing_reconfirmed_at, d.marketing_opt_in_at) as basis_at
            from public.push_devices d
            where d.marketing_opt_in
              and coalesce(d.marketing_reconfirmed_at, d.marketing_opt_in_at) <= p_basis_before
            order by basis_at
            limit p_limit
        ) c
    ), '[]'::jsonb));
end;
$function$;

revoke all on function public.gugu_push_reconfirm_candidates(timestamptz, int)
    from public, anon, authenticated;

create or replace function public.gugu_push_mark_reconfirm_notified(
    p_push_install_id uuid,
    p_expected_basis_at timestamptz
)
returns jsonb
language plpgsql
security definer
set search_path to ''
as $function$
declare
    v_user_id uuid;
    v_now timestamptz := clock_timestamp();
begin
    -- 발송 사이에 사용자가 동의를 바꿨다면 기준일을 덮어쓰지 않는다
    update public.push_devices
    set marketing_reconfirmed_at = v_now,
        updated_at = v_now
    where push_install_id = p_push_install_id
      and marketing_opt_in
      and coalesce(marketing_reconfirmed_at, marketing_opt_in_at) = p_expected_basis_at
    returning user_id into v_user_id;

    if v_user_id is null then
        return jsonb_build_object('updated', false);
    end if;

    -- 무응답이면 동의가 유지된다 — 확인 알림을 보낸 사실을 이력으로 남긴다
    insert into public.push_consent_events (
        user_id, push_install_id, kind, granted, source, recorded_at
    ) values (
        v_user_id, p_push_install_id, 'marketing', true, 'reconfirm_notice', v_now
    );
    return jsonb_build_object('updated', true, 'recordedAt', v_now);
end;
$function$;

revoke all on function public.gugu_push_mark_reconfirm_notified(uuid, timestamptz)
    from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 배치 실행 기록 — push_jobs.py 가 작업마다 남기고 /api/health/ready 가 읽는다
-- ---------------------------------------------------------------------------

create or replace function public.gugu_push_record_job_run(
    p_job text,
    p_status text,
    p_started_at timestamptz,
    p_detail jsonb
)
returns jsonb
language plpgsql
security definer
set search_path to ''
as $function$
declare
    v_id bigint;
begin
    if jsonb_typeof(p_detail) is distinct from 'object' then
        raise exception using errcode = '22023', message = 'INVALID_JOB_DETAIL';
    end if;

    insert into public.push_job_runs (job, status, started_at, detail)
    values (p_job, p_status, p_started_at, p_detail)
    returning id into v_id;

    return jsonb_build_object('id', v_id);
end;
$function$;

revoke all on function public.gugu_push_record_job_run(text, text, timestamptz, jsonb)
    from public, anon, authenticated;

create or replace function public.gugu_push_last_job_run()
returns jsonb
language sql
stable
security definer
set search_path to ''
as $function$
    -- 실패하지 않은 마지막 실행 — 매시 cron 이 살아 있는지 판단하는 근거
    select jsonb_build_object(
        'lastFinishedAt',
        (select max(finished_at) from public.push_job_runs where status <> 'failed')
    );
$function$;

revoke all on function public.gugu_push_last_job_run() from public, anon, authenticated;
