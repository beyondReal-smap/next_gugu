"""경로별 CORS 정책 분리.

한 앱에 성격이 다른 두 종류의 엔드포인트가 있다:
  - /api/iap/*, /api/premium/*, /api/account : Authorization(JWT)을 받는 결제·계정 API → 오리진 제한
  - /api/mcp/*                               : 인증 없는 공개 읽기 전용 MCP → 어디서든 호출 가능해야 함

Starlette의 CORSMiddleware는 앱 전체에 하나만 걸리고 preflight(OPTIONS)를 라우팅 전에
가로채기 때문에, 마운트된 sub-app에 CORS를 따로 걸어도 그 지점까지 요청이 도달하지 않는다.
그래서 경로를 보고 두 정책 중 하나로 넘기는 얇은 ASGI 미들웨어를 둔다.
"""

from starlette.middleware.cors import CORSMiddleware
from starlette.types import ASGIApp, Receive, Scope, Send


class ScopedCORSMiddleware:
    """path가 public_prefix로 시작하면 공개 CORS, 아니면 제한 CORS를 적용한다."""

    def __init__(
        self,
        app: ASGIApp,
        *,
        public_prefix: str,
        public_options: dict,
        default_options: dict,
    ) -> None:
        self.public_prefix = public_prefix
        self._public = CORSMiddleware(app, **public_options)
        self._default = CORSMiddleware(app, **default_options)

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        # lifespan/websocket은 CORS 대상이 아니다. CORSMiddleware가 그대로 통과시키므로 아무 쪽에 넘겨도 된다.
        if scope["type"] == "http" and scope["path"].startswith(self.public_prefix):
            await self._public(scope, receive, send)
            return
        await self._default(scope, receive, send)
