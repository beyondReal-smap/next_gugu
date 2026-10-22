-- 2026-10-19: 계정 삭제 정리 함수에 원격 푸시 데이터 삭제를 추가
--
-- 20261019_push.sql 이 만든 push_devices · push_consent_events · push_sends 도
-- 계정 삭제 시 함께 지운다. 세 테이블 모두 auth.users 를 on delete cascade 로 참조해
-- 인증 사용자 삭제만으로도 지워지지만, 감사 로그에 건수를 남기고 인증 사용자 삭제가
-- 실패해도 푸시 데이터는 먼저 사라지게 하려고 명시적으로 지운다.
-- 동의 이력 삭제는 미결정 8 "계정 삭제 시 동의 이력 삭제" 결정을 따른다.
--
-- 20260922_purge_owner_data.sql 의 본문은 그대로 두고 추가만 했다
-- (변수 3개, delete 3개, 반환 키 3개 — pushDevices · pushConsentEvents · pushSends).
--
-- 적용 순서: 20261019_push.sql 다음 (테이블이 없으면 함수 생성은 되지만 호출이 실패한다)
-- 되돌리기: 20260922_purge_owner_data.sql 을 다시 적용한다.

create or replace function public.gugu_purge_owner_data(p_owner_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path to ''
as $function$
declare
    v_guardian_links int := 0;
    v_sessions int := 0;
    v_learners int := 0;
    v_devices int := 0;
    v_coach int := 0;
    v_orphan_events int := 0;
    v_push_devices int := 0;
    v_push_consent_events int := 0;
    v_push_sends int := 0;
begin
    if p_owner_user_id is null then
        raise exception using errcode = '22023', message = 'OWNER_REQUIRED';
    end if;

    -- 보호자 쪽 링크 — learner 연쇄로도 지워지지만 이 계정이 보호자인 링크는 별도다
    delete from public.guardian_learner_links
    where guardian_user_id = p_owner_user_id;
    get diagnostics v_guardian_links = row_count;

    -- learners 를 거치지 않는 세션이 남아 devices 삭제를 막을 수 있어 먼저 지운다
    delete from public.learning_sessions
    where owner_user_id = p_owner_user_id;
    get diagnostics v_sessions = row_count;

    -- 대부분의 학습 데이터가 여기서 연쇄 삭제된다
    delete from public.learners
    where owner_user_id = p_owner_user_id;
    get diagnostics v_learners = row_count;

    delete from public.devices
    where owner_user_id = p_owner_user_id;
    get diagnostics v_devices = row_count;

    delete from public.coach_daily_usage
    where owner_user_id = p_owner_user_id;
    get diagnostics v_coach = row_count;

    -- 세션이 먼저 사라진 뒤 남은 이벤트가 있으면 함께 정리한다 (방어적)
    delete from public.answer_events
    where owner_user_id = p_owner_user_id;
    get diagnostics v_orphan_events = row_count;

    -- 원격 푸시: 기기 토큰·광고 동의 이력·발송 기록 (2026-10-19 추가)
    delete from public.push_devices
    where user_id = p_owner_user_id;
    get diagnostics v_push_devices = row_count;

    delete from public.push_consent_events
    where user_id = p_owner_user_id;
    get diagnostics v_push_consent_events = row_count;

    delete from public.push_sends
    where user_id = p_owner_user_id;
    get diagnostics v_push_sends = row_count;

    return jsonb_build_object(
        'guardianLinks', v_guardian_links,
        'learningSessions', v_sessions,
        'learners', v_learners,
        'devices', v_devices,
        'coachDailyUsage', v_coach,
        'orphanAnswerEvents', v_orphan_events,
        'pushDevices', v_push_devices,
        'pushConsentEvents', v_push_consent_events,
        'pushSends', v_push_sends
    );
end;
$function$;

revoke all on function public.gugu_purge_owner_data(uuid) from public, anon, authenticated;
