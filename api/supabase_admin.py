"""Supabase Admin API — service_role 키로 인증·학습 데이터 관리.

httpx 클라이언트는 모듈 수명으로 재사용.
"""
import logging
import re
from typing import Any, Optional

import httpx
from fastapi import HTTPException

from settings import settings

log = logging.getLogger("gugu-api")

SUPABASE_URL = str(settings.supabase_url).rstrip("/")
SUPABASE_SERVICE_ROLE_KEY = settings.supabase_service_role_key.get_secret_value()

_client: Optional[httpx.AsyncClient] = None


class SupabaseDataError(RuntimeError):
    """Supabase Data API 호출 실패."""

    def __init__(self, operation: str, status_code: int):
        super().__init__(f"{operation} 실패 ({status_code})")
        self.operation = operation
        self.status_code = status_code


def _get_client() -> httpx.AsyncClient:
    global _client
    if _client is None:
        _client = httpx.AsyncClient(timeout=15)
    return _client


def _admin_headers() -> dict[str, str]:
    return {
        "apikey": SUPABASE_SERVICE_ROLE_KEY,
        "Authorization": f"Bearer {SUPABASE_SERVICE_ROLE_KEY}",
        "Content-Type": "application/json",
    }


def _validate_resource_name(name: str) -> str:
    if not re.fullmatch(r"[a-z][a-z0-9_]*", name):
        raise ValueError("올바르지 않은 Supabase 리소스 이름입니다")
    return name


async def select_rows(table: str, params: dict[str, str]) -> list[dict[str, Any]]:
    """service_role로 PostgREST 테이블을 조회한다."""
    table = _validate_resource_name(table)
    res = await _get_client().get(
        f"{SUPABASE_URL}/rest/v1/{table}",
        headers=_admin_headers(),
        params=params,
    )
    if res.status_code != 200:
        log.error("Supabase 조회 실패 operation=select_%s status=%s", table, res.status_code)
        raise SupabaseDataError(f"select_{table}", res.status_code)
    try:
        data = res.json()
    except ValueError as exc:
        raise SupabaseDataError(f"select_{table}_invalid_json", res.status_code) from exc
    if not isinstance(data, list) or not all(isinstance(row, dict) for row in data):
        raise SupabaseDataError(f"select_{table}_invalid_response", res.status_code)
    return data


async def call_rpc(function_name: str, payload: dict[str, Any]) -> dict[str, Any]:
    """service_role 전용 Postgres 함수를 호출한다."""
    function_name = _validate_resource_name(function_name)
    res = await _get_client().post(
        f"{SUPABASE_URL}/rest/v1/rpc/{function_name}",
        headers=_admin_headers(),
        json=payload,
    )
    if res.status_code not in (200, 201):
        log.error("Supabase RPC 실패 operation=%s status=%s", function_name, res.status_code)
        raise SupabaseDataError(function_name, res.status_code)
    try:
        data = res.json()
    except ValueError as exc:
        raise SupabaseDataError(f"{function_name}_invalid_json", res.status_code) from exc
    if not isinstance(data, dict):
        raise SupabaseDataError(f"{function_name}_invalid_response", res.status_code)
    return data


async def upsert_rows(
    table: str,
    rows: list[dict[str, Any]],
    on_conflict: str,
) -> list[dict[str, Any]]:
    """service_role로 검증된 행을 멱등 upsert한다."""
    table = _validate_resource_name(table)
    conflict_columns = on_conflict.split(",")
    if not conflict_columns or any(_validate_resource_name(column) != column for column in conflict_columns):
        raise ValueError("올바르지 않은 Supabase 충돌 키입니다")
    headers = {
        **_admin_headers(),
        "Prefer": "resolution=merge-duplicates,return=representation",
    }
    res = await _get_client().post(
        f"{SUPABASE_URL}/rest/v1/{table}",
        headers=headers,
        params={"on_conflict": on_conflict},
        json=rows,
    )
    if res.status_code not in (200, 201):
        log.error("Supabase upsert 실패 operation=upsert_%s status=%s", table, res.status_code)
        raise SupabaseDataError(f"upsert_{table}", res.status_code)
    try:
        data = res.json()
    except ValueError as exc:
        raise SupabaseDataError(f"upsert_{table}_invalid_json", res.status_code) from exc
    if not isinstance(data, list) or not all(isinstance(row, dict) for row in data):
        raise SupabaseDataError(f"upsert_{table}_invalid_response", res.status_code)
    return data


async def delete_user(user_id: str) -> None:
    """Supabase auth 사용자 삭제. 실패 시 502로 표면화 (조용한 실패 금지)."""
    if not SUPABASE_URL or not SUPABASE_SERVICE_ROLE_KEY:
        raise HTTPException(503, "계정 삭제가 서버에 구성되지 않았습니다 (SUPABASE_SERVICE_ROLE_KEY 미설정)")

    res = await _get_client().delete(
        f"{SUPABASE_URL}/auth/v1/admin/users/{user_id}",
        headers=_admin_headers(),
    )
    # 404 = 이미 삭제된 사용자 — 멱등 처리
    if res.status_code not in (200, 204, 404):
        log.error("Supabase 사용자 삭제 실패 %s: %s", res.status_code, res.text[:200])
        raise HTTPException(502, "인증 서버에서 계정 삭제에 실패했습니다")

async def update_user_app_metadata(user_id: str, patch: dict[str, Any]) -> dict[str, Any]:
    """Supabase auth 사용자의 app_metadata 를 병합 갱신한다.

    app_metadata 는 service_role 로만 쓸 수 있는 관리자 속성이고, JWT 에 박혀 나가므로
    갱신 뒤에는 클라이언트가 토큰을 새로 받아야 권한이 반영된다.
    Supabase 는 전달한 키만 병합하고 나머지는 보존한다.
    """
    if not SUPABASE_URL or not SUPABASE_SERVICE_ROLE_KEY:
        raise HTTPException(
            503, "계정 권한 설정이 서버에 구성되지 않았습니다 (SUPABASE_SERVICE_ROLE_KEY 미설정)"
        )

    res = await _get_client().put(
        f"{SUPABASE_URL}/auth/v1/admin/users/{user_id}",
        headers={**_admin_headers(), "Content-Type": "application/json"},
        json={"app_metadata": patch},
    )
    if res.status_code != 200:
        log.error("app_metadata 갱신 실패 %s: %s", res.status_code, res.text[:200])
        raise HTTPException(502, "인증 서버에서 계정 권한 설정에 실패했습니다")
    try:
        return res.json()
    except ValueError as exc:
        raise HTTPException(502, "인증 서버 응답을 해석하지 못했습니다") from exc
