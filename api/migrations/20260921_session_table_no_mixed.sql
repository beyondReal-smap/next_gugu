-- 2026-09-21: 단이 섞인 세션의 이벤트 대량 거부 수정
--
-- gugu_ingest_learning_events 가 첫 이벤트로 learning_sessions 행을 만들고,
-- 이후 이벤트의 tableNo 가 그 행과 다르면 SESSION_MISMATCH 로 거부했다.
-- 그런데 API 계층(learning.py 의 LearningEvent.validate_fact_table)은 tableNo 가
-- factId 의 단과 같기를 요구한다 — 즉 문항별 값이다. 두 규칙이 서로 모순이라
-- 전체 랜덤·챌린지·서바이벌·OX·어드벤처처럼 단이 섞이는 모드의 이벤트가
-- 첫 문항의 단과 다르면 전부 버려졌다.
--
-- 실측(2026-09-21): 혼합 단 10문항 배치 → accepted=3, SESSION_MISMATCH=7
-- 적용 후            : accepted=10, session.table_no=null
--
-- 세션의 table_no 는 "이 세션이 한 단에 집중했는가"를 나타내는 메타데이터이므로,
-- 단이 섞였다는 사실은 null 로 표현하고 이벤트는 버리지 않는다.
-- 문항별 단은 answer_events.table_no 와 fact_id 에 그대로 남는다.
-- owner/learner/device/mode 의 세션 일관성 검사는 그대로 유지한다.
--
-- 되돌리기: 이 파일 이전 버전의 함수 정의를 다시 적용한다 (git 이력 참조).

CREATE OR REPLACE FUNCTION public.gugu_ingest_learning_events(p_owner_user_id uuid, p_events jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
    v_event jsonb;
    v_event_id_text text;
    v_event_id uuid;
    v_learner_id uuid;
    v_device_id uuid;
    v_session_id uuid;
    v_sequence_no integer;
    v_fact_id text;
    v_mode text;
    v_table_no smallint;
    v_submitted_answer jsonb;
    v_correct boolean;
    v_response_ms integer;
    v_attempt_no smallint;
    v_occurred_at timestamptz;
    v_content_version text;
    v_existing_owner uuid;
    v_session_owner uuid;
    v_session_learner uuid;
    v_session_device uuid;
    v_session_mode text;
    v_session_table smallint;
    v_inserted_id bigint;
    v_existing_event public.answer_events%rowtype;
    v_accepted jsonb := '[]'::jsonb;
    v_duplicates jsonb := '[]'::jsonb;
    v_rejected jsonb := '[]'::jsonb;
    v_touched_learners uuid[] := array[]::uuid[];
    v_server_cursor bigint := 0;
begin
    if jsonb_typeof(p_events) <> 'array'
       or jsonb_array_length(p_events) < 1
       or jsonb_array_length(p_events) > 100 then
        raise exception using errcode = '22023', message = 'INVALID_EVENT_BATCH';
    end if;

    for v_event in
        select value from jsonb_array_elements(p_events) as event_row(value)
    loop
        v_event_id_text := v_event ->> 'eventId';
        begin
            v_event_id := v_event_id_text::uuid;
            v_learner_id := (v_event ->> 'learnerId')::uuid;
            v_device_id := (v_event ->> 'deviceId')::uuid;
            v_session_id := (v_event ->> 'sessionId')::uuid;
            v_sequence_no := (v_event ->> 'sequenceNo')::integer;
            v_fact_id := v_event ->> 'factId';
            v_mode := v_event ->> 'mode';
            v_table_no := (v_event ->> 'tableNo')::smallint;
            v_submitted_answer := v_event -> 'submittedAnswer';
            v_correct := (v_event ->> 'correct')::boolean;
            v_response_ms := (v_event ->> 'responseMs')::integer;
            v_attempt_no := (v_event ->> 'attemptNo')::smallint;
            v_occurred_at := (v_event ->> 'occurredAt')::timestamptz;
            v_content_version := v_event ->> 'contentVersion';
        exception
            when invalid_text_representation or numeric_value_out_of_range then
                raise exception using errcode = '22023', message = 'INVALID_EVENT_PAYLOAD';
        end;

        if not exists (
            select 1
            from public.learners
            where id = v_learner_id
              and owner_user_id = p_owner_user_id
              and status = 'active'
        ) then
            v_rejected := v_rejected || jsonb_build_array(jsonb_build_object(
                'eventId', v_event_id,
                'code', 'LEARNER_NOT_FOUND'
            ));
            continue;
        end if;

        v_existing_owner := null;
        select owner_user_id
        into v_existing_owner
        from public.devices
        where id = v_device_id;

        if v_existing_owner is not null and v_existing_owner <> p_owner_user_id then
            v_rejected := v_rejected || jsonb_build_array(jsonb_build_object(
                'eventId', v_event_id,
                'code', 'DEVICE_OWNERSHIP_MISMATCH'
            ));
            continue;
        end if;

        insert into public.devices (id, owner_user_id, last_seen_at)
        values (v_device_id, p_owner_user_id, now())
        on conflict (id) do update
        set last_seen_at = excluded.last_seen_at
        where public.devices.owner_user_id = excluded.owner_user_id;

        v_session_owner := null;
        select owner_user_id, learner_id, device_id, mode, table_no
        into v_session_owner, v_session_learner, v_session_device, v_session_mode, v_session_table
        from public.learning_sessions
        where id = v_session_id;

        if v_session_owner is null then
            insert into public.learning_sessions (
                id,
                owner_user_id,
                learner_id,
                device_id,
                mode,
                table_no,
                started_at,
                ended_at,
                content_version
            ) values (
                v_session_id,
                p_owner_user_id,
                v_learner_id,
                v_device_id,
                v_mode,
                v_table_no,
                v_occurred_at,
                v_occurred_at,
                v_content_version
            )
            on conflict (id) do nothing;

            select owner_user_id, learner_id, device_id, mode, table_no
            into v_session_owner, v_session_learner, v_session_device, v_session_mode, v_session_table
            from public.learning_sessions
            where id = v_session_id;
        end if;

        -- 단이 섞인 세션은 세션 table_no 를 null(혼합)로 승격시킨다.
        --
        -- 예전에는 첫 이벤트의 단과 다른 이벤트를 SESSION_MISMATCH 로 거부했다.
        -- 그런데 API 계층(LearningEvent.validate_fact_table)은 tableNo 가 factId 의
        -- 단과 같기를 요구한다 — 즉 문항별 값이다. 두 규칙이 서로 모순이어서,
        -- 전체 랜덤·챌린지·서바이벌·OX·어드벤처처럼 단이 섞이는 모드의 이벤트가
        -- 첫 문항의 단과 다르면 전부 버려졌다(2026-09-21 실측: 10건 중 7건 거부).
        --
        -- 세션의 table_no 는 "이 세션이 한 단에 집중했는가"를 나타내는 메타데이터다.
        -- 단이 섞였다는 사실은 null 로 표현하는 것이 맞고, 이벤트를 버릴 이유가 없다.
        -- 문항별 단은 answer_events.table_no 와 fact_id 에 그대로 남는다.
        if v_session_table is not null and v_session_table is distinct from v_table_no then
            update public.learning_sessions
            set table_no = null
            where id = v_session_id;
            v_session_table := null;
        end if;

        if v_session_owner is distinct from p_owner_user_id
           or v_session_learner is distinct from v_learner_id
           or v_session_device is distinct from v_device_id
           or v_session_mode is distinct from v_mode then
            v_rejected := v_rejected || jsonb_build_array(jsonb_build_object(
                'eventId', v_event_id,
                'code', 'SESSION_MISMATCH'
            ));
            continue;
        end if;

        v_inserted_id := null;
        insert into public.answer_events (
            event_id,
            owner_user_id,
            learner_id,
            device_id,
            session_id,
            sequence_no,
            fact_id,
            mode,
            table_no,
            submitted_answer,
            correct,
            response_ms,
            attempt_no,
            occurred_at,
            content_version
        ) values (
            v_event_id,
            p_owner_user_id,
            v_learner_id,
            v_device_id,
            v_session_id,
            v_sequence_no,
            v_fact_id,
            v_mode,
            v_table_no,
            v_submitted_answer,
            v_correct,
            v_response_ms,
            v_attempt_no,
            v_occurred_at,
            v_content_version
        )
        on conflict (event_id, device_id) do nothing
        returning id into v_inserted_id;

        if v_inserted_id is null then
            select *
            into v_existing_event
            from public.answer_events
            where event_id = v_event_id
              and device_id = v_device_id;

            if v_existing_event.owner_user_id = p_owner_user_id
               and v_existing_event.learner_id = v_learner_id
               and v_existing_event.session_id = v_session_id
               and v_existing_event.sequence_no = v_sequence_no
               and v_existing_event.fact_id = v_fact_id
               and v_existing_event.mode = v_mode
               and v_existing_event.table_no is not distinct from v_table_no
               and v_existing_event.submitted_answer = v_submitted_answer
               and v_existing_event.correct = v_correct
               and v_existing_event.response_ms = v_response_ms
               and v_existing_event.attempt_no = v_attempt_no
               and v_existing_event.occurred_at = v_occurred_at
               and v_existing_event.content_version = v_content_version then
                v_duplicates := v_duplicates || jsonb_build_array(v_event_id);
            else
                v_rejected := v_rejected || jsonb_build_array(jsonb_build_object(
                    'eventId', v_event_id,
                    'code', 'IDEMPOTENCY_CONFLICT'
                ));
            end if;
            continue;
        end if;

        v_accepted := v_accepted || jsonb_build_array(v_event_id);
        v_server_cursor := greatest(v_server_cursor, v_inserted_id);

        update public.learning_sessions
        set started_at = least(started_at, v_occurred_at),
            ended_at = greatest(coalesce(ended_at, v_occurred_at), v_occurred_at)
        where id = v_session_id;

        insert into public.sync_cursors (
            learner_id,
            device_id,
            owner_user_id,
            server_cursor,
            last_event_received_at
        ) values (
            v_learner_id,
            v_device_id,
            p_owner_user_id,
            v_inserted_id,
            clock_timestamp()
        )
        on conflict (learner_id, device_id) do update
        set server_cursor = greatest(public.sync_cursors.server_cursor, excluded.server_cursor),
            last_event_received_at = excluded.last_event_received_at;

        if not (v_learner_id = any(v_touched_learners)) then
            v_touched_learners := array_append(v_touched_learners, v_learner_id);
        end if;
    end loop;

    foreach v_learner_id in array v_touched_learners
    loop
        perform public.gugu_rebuild_progress_snapshot(p_owner_user_id, v_learner_id);
    end loop;

    if v_server_cursor = 0 then
        select coalesce(max(id), 0)
        into v_server_cursor
        from public.answer_events
        where owner_user_id = p_owner_user_id;
    end if;

    return jsonb_build_object(
        'acceptedEventIds', v_accepted,
        'duplicateEventIds', v_duplicates,
        'rejected', v_rejected,
        'serverCursor', v_server_cursor
    );
end;
$function$

;
