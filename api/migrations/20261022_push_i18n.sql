-- 2026-10-22: 주간 리포트 후보에 기기 locale · time_zone 을 함께 돌려준다 (iOS 다국어 2.6.0)
--
-- API(push_jobs.run_weekly_report)가 기기마다 앱 언어로 문구를 고르고, 기기 현지 시각
-- 일 19:00 ~ 월 20:00(08~21시)에만 보낸다. 두 값은 20261019_push.sql 의 push_devices 에
-- 이미 저장되고 있어 테이블 변경은 없다. 20261019_push.sql 의 함수 본문은 그대로 두고
-- devices 객체에 키 두 개(locale · timeZone)만 추가했다.
--
-- 호환: 이 migration 전 API 는 두 키를 무시하고, 이 migration 전 RPC 를 새 API 가 받으면
-- 두 값이 없는 것으로(한국어·KST) 처리한다 — 적용 순서와 무관하게 동작한다.
-- 적용 순서: 20261019_push.sql 다음

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
                    'timeZone', d.time_zone
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
