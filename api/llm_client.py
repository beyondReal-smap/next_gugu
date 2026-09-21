"""AI 코치 전용 OpenAI Responses API 클라이언트."""

import json
from typing import Any, Optional

import httpx
from pydantic import BaseModel

from settings import settings


class CoachProviderError(RuntimeError):
    """코치 제공자 호출 또는 응답 검증 실패."""


_client: Optional[httpx.AsyncClient] = None


def is_enabled() -> bool:
    return settings.coach_llm_api_key is not None and settings.coach_llm_model is not None


def _get_client() -> httpx.AsyncClient:
    global _client
    if _client is None:
        _client = httpx.AsyncClient(timeout=settings.coach_llm_timeout_seconds)
    return _client


def _extract_output_text(data: object) -> str:
    if not isinstance(data, dict) or data.get("status") != "completed":
        raise CoachProviderError("코치 제공자가 완료 응답을 반환하지 않았습니다")
    texts: list[str] = []
    output = data.get("output")
    if not isinstance(output, list):
        raise CoachProviderError("코치 제공자 출력 형식이 올바르지 않습니다")
    for item in output:
        if not isinstance(item, dict) or item.get("type") != "message":
            continue
        content = item.get("content")
        if not isinstance(content, list):
            continue
        for part in content:
            if isinstance(part, dict) and part.get("type") == "output_text" and isinstance(part.get("text"), str):
                texts.append(part["text"])
    if not texts:
        raise CoachProviderError("코치 제공자 응답에 텍스트가 없습니다")
    return "".join(texts)


async def generate_structured_coach(
    grounding: dict[str, Any],
    locale: str,
    output_model: type[BaseModel],
) -> BaseModel:
    if not is_enabled():
        raise CoachProviderError("코치 제공자가 비활성 상태입니다")
    instructions = (
        "당신은 초등 곱셈 코치입니다. 입력 JSON의 reading, commonMistake, strategy 문장만 근거로 사용하십시오. "
        "새 사실, 새 학습법, 학습자 개인정보를 만들거나 추론하지 마십시오. hint는 두 문장 이하, "
        "checkQuestion은 제공된 factId 하나를 다시 확인하는 짧은 질문이어야 합니다. 지정된 JSON 스키마만 반환하십시오."
    )
    payload = {
        "model": settings.coach_llm_model,
        "instructions": instructions,
        "input": json.dumps({"locale": locale, "grounding": grounding}, ensure_ascii=False),
        "text": {
            "format": {
                "type": "json_schema",
                "name": "coach_explanation",
                "strict": True,
                "schema": output_model.model_json_schema(),
            }
        },
        "max_output_tokens": 300,
        "store": False,
    }
    headers = {
        "Authorization": f"Bearer {settings.coach_llm_api_key.get_secret_value()}",
        "Content-Type": "application/json",
    }
    try:
        response = await _get_client().post(
            f"{str(settings.coach_llm_base_url).rstrip('/')}/responses",
            headers=headers,
            json=payload,
        )
        response.raise_for_status()
        data = response.json()
    except (httpx.TimeoutException, httpx.HTTPStatusError, httpx.RequestError, ValueError) as exc:
        raise CoachProviderError("코치 제공자 호출에 실패했습니다") from exc
    try:
        return output_model.model_validate_json(_extract_output_text(data))
    except ValueError as exc:
        raise CoachProviderError("코치 제공자 응답 스키마가 올바르지 않습니다") from exc
