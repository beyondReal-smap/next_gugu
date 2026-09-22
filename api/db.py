"""MariaDB 연결·스키마·구매 쿼리 모듈.

- db_conn: 커넥션 생성 (main.py에서 이관)
- ensure_schema: 기동 시 purchases.user_id 컬럼/인덱스 멱등 추가
- 구매 기록·계정 연결·프리미엄 판정 쿼리
"""
import logging
from datetime import datetime
from typing import Optional

import pymysql

from settings import settings

log = logging.getLogger("gugu-api")

DB_SOCKET = str(settings.db_socket)
DB_USER = settings.db_user
DB_PASSWORD = settings.db_password.get_secret_value()
DB_NAME = settings.db_name


class PurchasePersistenceError(RuntimeError):
    """스토어 검증은 성공했지만 구매 원장 저장에 실패함."""


class DatabaseHealthError(RuntimeError):
    """준비 상태 검사에서 데이터베이스 연결에 실패함."""


class PremiumLookupError(RuntimeError):
    """프리미엄 권한 조회에 실패함."""


def db_conn():
    return pymysql.connect(
        unix_socket=DB_SOCKET, user=DB_USER, password=DB_PASSWORD,
        database=DB_NAME, charset="utf8mb4", autocommit=True,
    )


def _has_column(cur, column: str) -> bool:
    cur.execute(
        """SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = %s AND TABLE_NAME = 'purchases' AND COLUMN_NAME = %s""",
        (DB_NAME, column),
    )
    return cur.fetchone()[0] > 0


def ensure_schema() -> None:
    """purchases의 추가 컬럼을 기동 시 멱등 추가.

    - user_id: Supabase auth.users.id (UUID 문자열) — 구매를 계정에 연결
    - environment: Production/Sandbox — 샌드박스 거래로 부여된 권한을 사후 식별·정리
    """
    # TODO: 정식 마이그레이션 체계를 도입하면 기동 DDL을 배포 전 단계로 옮긴다.
    with db_conn() as conn, conn.cursor() as cur:
        if not _has_column(cur, "user_id"):
            cur.execute(
                """ALTER TABLE purchases
                   ADD COLUMN user_id VARCHAR(36) NULL AFTER product_id,
                   ADD INDEX idx_user_product (user_id, product_id)"""
            )
            log.info("purchases.user_id 컬럼 추가 완료")
        if not _has_column(cur, "environment"):
            cur.execute(
                "ALTER TABLE purchases ADD COLUMN environment VARCHAR(16) NULL AFTER user_id"
            )
            log.info("purchases.environment 컬럼 추가 완료")


def record_purchase(
    platform: str, transaction_id: str, product_id: str, raw_status: str,
    user_id: Optional[str] = None, environment: Optional[str] = None,
) -> None:
    """구매 기록 upsert — 동일 거래 재검증은 멱등 성공으로 처리한다.

    user_id·environment는 값이 주어졌을 때만 갱신(COALESCE) — 게스트 재검증이나
    환경 정보가 없는 레거시 경로가 기존 계정 연결·환경 기록을 지우지 않는다.
    """
    try:
        with db_conn() as conn, conn.cursor() as cur:
            cur.execute(
                """INSERT INTO purchases
                     (platform, transaction_id, product_id, user_id, environment, raw_status, verified_at)
                   VALUES (%s, %s, %s, %s, %s, %s, %s)
                   ON DUPLICATE KEY UPDATE
                     raw_status = VALUES(raw_status),
                     verified_at = VALUES(verified_at),
                     user_id = COALESCE(VALUES(user_id), user_id),
                     environment = COALESCE(VALUES(environment), environment)""",
                (platform, transaction_id, product_id, user_id, environment, raw_status,
                 datetime.utcnow()),
            )
    except pymysql.MySQLError as exc:
        log.exception("구매 기록 저장 실패")
        raise PurchasePersistenceError("구매 원장 저장에 실패했습니다") from exc


def check_database() -> None:
    """준비 상태 확인용 최소 쿼리."""
    try:
        with db_conn() as conn, conn.cursor() as cur:
            cur.execute("SELECT 1")
            if cur.fetchone() != (1,):
                raise DatabaseHealthError("데이터베이스 준비 상태 응답이 올바르지 않습니다")
    except pymysql.MySQLError as exc:
        raise DatabaseHealthError("데이터베이스에 연결할 수 없습니다") from exc


def user_premium_products(user_id: str) -> list[str]:
    """계정에 연결된 검증 완료 구매의 product_id 목록."""
    try:
        with db_conn() as conn, conn.cursor() as cur:
            cur.execute(
                """SELECT DISTINCT product_id FROM purchases
                   WHERE user_id = %s AND raw_status = 'verified'""",
                (user_id,),
            )
            return [row[0] for row in cur.fetchall()]
    except pymysql.MySQLError as exc:
        raise PremiumLookupError("프리미엄 권한을 조회할 수 없습니다") from exc


def unlink_user_purchases(user_id: str) -> int:
    """계정 삭제 시 구매-계정 연결만 해제 (영수증 기록은 환불/감사 대비 보존)."""
    with db_conn() as conn, conn.cursor() as cur:
        return cur.execute(
            "UPDATE purchases SET user_id = NULL WHERE user_id = %s", (user_id,)
        )
