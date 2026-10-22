-- 2026-10-23: 주간 리포트 후보 devices 에 appVersion 을 함께 돌려준다 (Android 다국어 대비)
--
-- 2.6 이전 앱(iOS 2.5.x · Android 2.5.x)은 화면이 한국어뿐인데 locale 에 기기 언어("en-US")를 보냈다.
-- 20261022_push_i18n.sql 이후 API 가 locale 로 문구 언어를 고르므로, 기기 언어가 영어인 한국 사용자가
-- 영어 주간 리포트를 받게 된다. API(push_templates.language_for_device)가 appVersion < 2.6 이면 ko 로 보낸다.
-- 20261022_push_i18n.sql 의 함수 본문은 그대로 두고 devices 객체에 키 하나(appVersion)만 추가했다.
--
-- 호환: 이 migration 전 RPC 를 새 API 가 받으면 appVersion 이 없는 것으로(locale 만 사용) 처리하고,
-- 이전 API 는 키를 무시한다 — 적용 순서와 무관하게 동작한다.
-- 적용 순서: 20261022_push_i18n.sql 다음

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
                    'platform', d.platform,
                    -- 다국어(2026-10-22): 알림 문구 언어와 현지 발송 시각을 고르는 값. 2.6 이전 기기는 null
                    'locale', d.locale,
                    'timeZone', d.time_zone,
                    -- 2026-10-23: 2.6 이전 앱(한국어 전용 화면)을 가려 ko 로 보내는 값
                    'appVersion', d.app_version
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
