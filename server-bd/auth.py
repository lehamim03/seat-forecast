"""Firebase ID 토큰 확인 — 앱이 보내는 Authorization: Bearer <토큰> 의 사용자(uid).

SEAT_AUTH 환경 변수:
  firebase (기본)  Firebase Admin SDK 로 확인. FIREBASE_AUTH_EMULATOR_HOST(예: 127.0.0.1:9099) 가 있으면
                   로컬 에뮬레이터 토큰을 받고, 실제 프로젝트는 FIREBASE_PROJECT_ID 로 정함 (공개 키로 서명 확인).
  off              확인하지 않고 토큰 문자열을 uid 로 씀 — 내 컴퓨터에서 화면만 볼 때 (배포 금지)
"""
from __future__ import annotations

import os
from typing import Optional

_app = None


def mode() -> str:
    return os.environ.get("SEAT_AUTH", "firebase")


def _firebase():
    global _app
    if _app is None:
        import firebase_admin
        from firebase_admin import credentials
        from google.auth.credentials import AnonymousCredentials

        class NoKey(credentials.Base):
            """ID 토큰 확인은 공개 키만 쓰므로 서비스 계정 키가 필요 없음"""

            def get_credential(self):
                return AnonymousCredentials()

        project = os.environ.get("FIREBASE_PROJECT_ID", "demo-seat-forecast")
        _app = firebase_admin.initialize_app(NoKey(), options={"projectId": project}, name="seat-bd")
    return _app


def verify(token: str) -> Optional[str]:
    """올바른 토큰이면 uid, 아니면 None"""
    if mode() == "off":
        return token
    from firebase_admin import auth
    try:
        decoded = auth.verify_id_token(token, app=_firebase())
    except Exception:
        return None
    return decoded.get("uid")
