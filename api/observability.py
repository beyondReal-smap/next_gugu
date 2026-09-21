"""요청 식별자와 최소 구조화 요청 로그."""

import hashlib
import json
import logging
import re
import time
from uuid import uuid4

from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.requests import Request
from starlette.responses import Response


log = logging.getLogger("gugu-api.requests")
_REQUEST_ID_RE = re.compile(r"[A-Za-z0-9._:-]{1,128}")

if not log.handlers:
    _handler = logging.StreamHandler()
    _handler.setFormatter(logging.Formatter("%(message)s"))
    log.addHandler(_handler)
    log.setLevel(logging.INFO)
    log.propagate = False


def mask_identifier(value: str | None) -> str:
    """로그 상관관계는 유지하면서 원문 식별자를 숨긴다."""
    if not value:
        return "-"
    digest = hashlib.sha256(value.encode("utf-8")).hexdigest()[:12]
    return f"sha256:{digest}"


def _request_id(request: Request) -> str:
    incoming = request.headers.get("X-Request-ID", "")
    if _REQUEST_ID_RE.fullmatch(incoming):
        return incoming
    return uuid4().hex


class RequestIdMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        request_id = _request_id(request)
        request.state.request_id = request_id
        started = time.perf_counter()
        status_code = 500
        try:
            response = await call_next(request)
            status_code = response.status_code
        except Exception:
            elapsed_ms = round((time.perf_counter() - started) * 1000, 2)
            log.exception(
                json.dumps(
                    {
                        "event": "http_request",
                        "requestId": request_id,
                        "method": request.method,
                        "path": request.url.path,
                        "status": status_code,
                        "durationMs": elapsed_ms,
                    },
                    ensure_ascii=False,
                )
            )
            raise

        response.headers["X-Request-ID"] = request_id
        elapsed_ms = round((time.perf_counter() - started) * 1000, 2)
        log.info(
            json.dumps(
                {
                    "event": "http_request",
                    "requestId": request_id,
                    "method": request.method,
                    "path": request.url.path,
                    "status": status_code,
                    "durationMs": elapsed_ms,
                },
                ensure_ascii=False,
            )
        )
        return response
