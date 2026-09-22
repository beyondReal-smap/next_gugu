-- 2026-09-22: 계정 삭제 시 학습 데이터까지 지우는 정리 함수
--
-- DELETE /api/account 는 구매 연결만 해제하고 Supabase 인증 사용자를 지웠다.
-- 그런데 auth.users 를 참조하는 FK 가 하나도 없어서 학습 데이터가 전부 고아로 남았다.
-- 개인정보처리방침은 "계정과 서버에 보관된 학습 기록이 삭제됩니다" 라고 약속하므로
-- 사실과 달랐다. 인증 사용자 삭제 전에 이 함수로 소유 데이터를 먼저 지운다.
--
-- 삭제 순서가 중요하다 — learning_sessions.device_id → devices 가 RESTRICT 이므로
-- devices 를 먼저 지우려 하면 실패한다. learners 를 지우면 아래가 연쇄로 정리된다.
--   progress_snapshots, sync_cursors, fact_mastery, practice_plans(→items),
--   device_learner_claims, guardian_learner_links, learning_sessions(→answer_events)
-- 그 다음 devices, 마지막으로 learners 를 거치지 않는 coach_daily_usage 를 지운다.
--
-- 구매 영수증(MariaDB purchases)은 환불·감사 대응을 위해 보존하고 계정 연결만
-- 해제한다. 이는 API 쪽 unlink_user_purchases 가 담당한다.
--
-- 반환: 테이블별 삭제 행 수 (감사 로그용)

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

    return jsonb_build_object(
        'guardianLinks', v_guardian_links,
        'learningSessions', v_sessions,
        'learners', v_learners,
        'devices', v_devices,
        'coachDailyUsage', v_coach,
        'orphanAnswerEvents', v_orphan_events
    );
end;
$function$;

revoke all on function public.gugu_purge_owner_data(uuid) from public, anon, authenticated;
