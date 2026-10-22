"""운영자 개별 알림 발송 CLI — 정보성 템플릿만 보낸다(미결정 6 "운영자 개별 발송은 CLI").

    uv run python push_admin.py send-user --email user@example.com --template premium_granted --dry-run
    uv run python push_admin.py send-user --email user@example.com --template account_notice \\
        --title "계정 안내" --body "문의하신 내용을 처리했어요. 앱에서 확인해 주세요."
    uv run python push_admin.py send-token --token <FCM 토큰> --template test

- 광고성 문구는 거부한다. 광고는 Firebase 콘솔 promo 토픽으로만 보낸다(README).
- 21:00~07:59 KST 에는 --force 가 있어야 보낸다.
- 출력·로그에는 이메일·토큰 원문을 남기지 않는다(마스킹).
- 모든 실제 발송은 push_sends 에 기록된다(dedupe_key admin:<템플릿>:<무작위>).
"""

import argparse
import asyncio
import sys
from datetime import datetime, timezone
from typing import Optional
from uuid import uuid4

from pydantic import BaseModel, ValidationError

import fcm
import supabase_admin
from observability import mask_email, mask_identifier
from push_delivery import PushStoreContractError, deliver
from push_schedule import is_daytime, to_kst
from push_templates import OPERATOR_TEMPLATES, TemplateError, render


class _Device(BaseModel):
    pushInstallId: str
    fcmToken: str
    platform: str


class _DevicesForEmail(BaseModel):
    found: bool
    userId: Optional[str] = None
    devices: list[_Device] = []


def _check_quiet_hours(force: bool, dry_run: bool, now: datetime) -> None:
    if is_daytime(now):
        return
    if dry_run:
        print(f"⚠️  지금은 야간({to_kst(now):%H:%M} KST)입니다 — 실제 발송에는 --force 가 필요합니다")
        return
    if not force:
        raise SystemExit(
            f"⛔ 지금은 야간({to_kst(now):%H:%M} KST)입니다. 08:00~20:59 에 보내거나, "
            "꼭 필요하면 --force 로 다시 실행하세요."
        )
    print(f"⚠️  야간 발송({to_kst(now):%H:%M} KST) — --force 로 진행합니다")


def _render(args: argparse.Namespace):
    try:
        return render(args.template, lang=args.lang, title=args.title, body=args.body)
    except TemplateError as exc:
        raise SystemExit(f"⛔ 문구 거부: {exc}") from None


async def _send_user(args: argparse.Namespace) -> int:
    message = _render(args)
    _check_quiet_hours(args.force, args.dry_run, datetime.now(timezone.utc))
    raw = await supabase_admin.call_rpc("gugu_push_devices_for_email", {"p_email": args.email})
    try:
        lookup = _DevicesForEmail.model_validate(raw)
    except ValidationError as exc:
        raise PushStoreContractError("gugu_push_devices_for_email 응답 계약 오류") from exc

    who = mask_email(args.email)
    if not lookup.found or lookup.userId is None:
        print(f"⛔ 계정을 찾을 수 없습니다: {who}")
        return 1
    platforms = {"ios": 0, "android": 0}
    for device in lookup.devices:
        platforms[device.platform] = platforms.get(device.platform, 0) + 1
    print(f"대상: {who} user={mask_identifier(lookup.userId)} 기기 {len(lookup.devices)}대 {platforms}")
    print(f"제목: {message.title}\n본문: {message.body}\n링크: {message.link} 채널: {message.android_channel}")
    if not lookup.devices:
        print("⛔ 알림을 켠 기기가 없습니다 — 보낼 수 없습니다")
        return 1
    if args.dry_run:
        print("🧪 dry-run — 보내지 않았습니다")
        return 0

    report = await deliver(
        fcm.get_sender(),
        dedupe_key=f"admin:{args.template}:{uuid4().hex}",
        kind=args.template,
        user_id=lookup.userId,
        tokens=[device.fcmToken for device in lookup.devices],
        message=message,
    )
    print(
        f"{'✅' if report.status == 'sent' else '⚠️ '} 결과 {report.status}: 성공 {report.success} · "
        f"실패 {report.failure} · 무효 토큰 정리 {report.pruned}"
    )
    return 0 if report.status == "sent" else 1


async def _send_token(args: argparse.Namespace) -> int:
    message = _render(args)
    _check_quiet_hours(args.force, args.dry_run, datetime.now(timezone.utc))
    print(f"대상 토큰: {mask_identifier(args.token)}")
    print(f"제목: {message.title}\n본문: {message.body}\n링크: {message.link} 채널: {message.android_channel}")
    if args.dry_run:
        print("🧪 dry-run — 보내지 않았습니다")
        return 0
    report = await deliver(
        fcm.get_sender(),
        dedupe_key=f"admin:{args.template}:{uuid4().hex}",
        kind=args.template,
        user_id=None,
        tokens=[args.token],
        message=message,
    )
    print(f"{'✅' if report.status == 'sent' else '⚠️ '} 결과 {report.status} (무효 토큰 정리 {report.pruned})")
    return 0 if report.status == "sent" else 1


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="구구 어드벤처 운영자 개별 알림 발송(정보성만)")
    sub = parser.add_subparsers(dest="command", required=True)

    def add_common(p: argparse.ArgumentParser) -> None:
        p.add_argument("--template", required=True, choices=OPERATOR_TEMPLATES)
        p.add_argument("--title", help="템플릿 제목 대신 쓸 문구(광고 표현 금지)")
        p.add_argument("--body", help="템플릿 본문 대신 쓸 문구(광고 표현·학습 수치 금지)")
        p.add_argument("--lang", choices=("ko", "en", "ja"), default="ko",
                       help="템플릿 문구 언어 — 받는 사람 앱 언어에 맞춘다 (기본 ko)")
        p.add_argument("--dry-run", action="store_true", help="대상·문구만 확인하고 보내지 않는다")
        p.add_argument("--force", action="store_true", help="야간(21~08시 KST)에도 보낸다")

    user = sub.add_parser("send-user", help="이메일 계정의 알림 켠 기기 전체에 보낸다")
    user.add_argument("--email", required=True)
    add_common(user)

    token = sub.add_parser("send-token", help="FCM 토큰 하나에 보낸다(테스트 기기)")
    token.add_argument("--token", required=True)
    add_common(token)
    return parser


async def _main(argv: list[str]) -> int:
    args = build_parser().parse_args(argv)
    try:
        if args.command == "send-user":
            return await _send_user(args)
        return await _send_token(args)
    except fcm.PushUnavailable as exc:
        print(f"⛔ 푸시 발송이 설정되지 않았습니다: {exc}")
        return 1
    except fcm.FcmAuthError as exc:
        print(f"⛔ FCM 인증/권한 오류로 중단했습니다: {exc} — 서비스 계정 역할·프로젝트 ID 를 확인하세요")
        return 1
    except supabase_admin.SupabaseDataError as exc:
        print(f"⛔ 푸시 저장소 호출 실패: {exc.operation} ({exc.status_code})")
        return 1
    finally:
        await fcm.close_sender()


if __name__ == "__main__":
    sys.exit(asyncio.run(_main(sys.argv[1:])))
