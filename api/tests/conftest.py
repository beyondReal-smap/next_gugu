"""테스트 공통 설정 — settings.py 가 임포트 시 필수 환경변수를 검증하므로 먼저 채운다.

실제 서비스에 닿지 않도록 로컬 .env 보다 우선하는 가짜 값을 강제로 넣는다
(load_dotenv 는 이미 있는 환경변수를 덮어쓰지 않는다).
"""

import os

_TEST_ENV = {
    "PRODUCT_ID": "site.smap.gugudan.premium",
    "ANDROID_PACKAGE": "site.smap.gugudan",
    "DB_SOCKET": "/tmp/gugu-test-mysql.sock",
    "DB_USER": "test",
    "DB_PASSWORD": "test",
    "DB_NAME": "test",
    "SUPABASE_URL": "https://example.supabase.co",
    "SUPABASE_SERVICE_ROLE_KEY": "test-service-role",
    "FIREBASE_PROJECT_ID": "",
    "FIREBASE_SERVICE_ACCOUNT_JSON": "",
}
os.environ.update(_TEST_ENV)
