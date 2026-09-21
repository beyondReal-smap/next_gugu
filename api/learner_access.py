"""서비스 역할 호출 전 보호자-학습자 접근 계약 검증."""

from uuid import UUID

from fastapi import HTTPException

import supabase_admin
from learning import error_detail, learning_store_unavailable


async def require_guardian_learner_access(user_id: str, learner_id: UUID) -> None:
    try:
        rows = await supabase_admin.select_rows(
            "guardian_learner_links",
            {
                "guardian_user_id": f"eq.{user_id}",
                "learner_id": f"eq.{learner_id}",
                "status": "eq.active",
                "select": "learner_id",
                "limit": "1",
            },
        )
    except supabase_admin.SupabaseDataError as exc:
        raise learning_store_unavailable(exc) from exc
    if not rows:
        raise HTTPException(
            404,
            detail=error_detail("LEARNER_NOT_FOUND", "접근 가능한 학습자를 찾을 수 없습니다"),
        )
