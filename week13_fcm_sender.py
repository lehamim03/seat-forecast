"""
좌석예보 Week 13 - FCM 알림 전송 모듈

E-notification 담당 기능:
- Week 11~12에서 정리한 FCM payload 구조를 실제 전송 함수 형태로 확장
- 사용자 FCM Token을 받아 돌발 알림, 출발 알림, 대체 경로 알림 전송 준비
- Firebase Admin SDK 연동 시 사용할 수 있는 기본 코드 구조 작성

주의:
- 실제 Firebase 서비스 계정 키(serviceAccountKey.json)는 GitHub에 올리지 않는다.
- 이 파일은 전송 구조를 정리한 코드이며, 실제 실행 시에는 Firebase Admin SDK 설정이 필요하다.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Literal


AlertType = Literal[
    "CONGESTION_ALERT",
    "DEPARTURE_ALERT",
    "ALTERNATIVE_ROUTE_ALERT",
    "DELAY_ALERT",
]


@dataclass
class FcmNotification:
    """좌석예보 FCM 알림 데이터 모델"""

    token: str
    title: str
    body: str
    alert_type: AlertType
    route_id: str
    target_screen: str
    extra_data: dict[str, str] | None = None

    def to_payload(self) -> dict[str, Any]:
        """Firebase Cloud Messaging 전송용 payload로 변환한다."""
        data = {
            "type": self.alert_type,
            "routeId": self.route_id,
            "targetScreen": self.target_screen,
        }

        if self.extra_data:
            data.update(self.extra_data)

        return {
            "message": {
                "token": self.token,
                "notification": {
                    "title": self.title,
                    "body": self.body,
                },
                "data": data,
            }
        }


def create_congestion_notification(
    token: str,
    route_id: str,
    alternative_route: str,
) -> FcmNotification:
    """만석 위험 알림 생성"""
    return FcmNotification(
        token=token,
        title="좌석예보",
        body=f"{route_id}번 버스 만석 위험이 높아졌습니다. {alternative_route}번 대체 경로를 확인해보세요.",
        alert_type="CONGESTION_ALERT",
        route_id=route_id,
        target_screen="alternative_route",
        extra_data={
            "alternativeRoute": alternative_route,
        },
    )


def create_departure_notification(
    token: str,
    route_id: str,
    departure_time: str,
) -> FcmNotification:
    """출발 시각 알림 생성"""
    return FcmNotification(
        token=token,
        title="좌석예보",
        body=f"지금 출발하면 {departure_time} {route_id}번 버스를 탈 가능성이 높습니다.",
        alert_type="DEPARTURE_ALERT",
        route_id=route_id,
        target_screen="recommended_route",
        extra_data={
            "departureTime": departure_time,
        },
    )


def create_alternative_route_notification(
    token: str,
    current_route: str,
    alternative_route: str,
) -> FcmNotification:
    """대체 경로 추천 알림 생성"""
    return FcmNotification(
        token=token,
        title="좌석예보",
        body=f"현재 {current_route}번 경로의 탑승 성공 확률이 낮습니다. {alternative_route}번 노선을 추천합니다.",
        alert_type="ALTERNATIVE_ROUTE_ALERT",
        route_id=current_route,
        target_screen="alternative_route",
        extra_data={
            "currentRoute": current_route,
            "alternativeRoute": alternative_route,
        },
    )


def create_delay_notification(
    token: str,
    route_id: str,
    delay_minutes: int,
) -> FcmNotification:
    """돌발 정체 / 지연 알림 생성"""
    return FcmNotification(
        token=token,
        title="좌석예보",
        body=f"{route_id}번 노선에 약 {delay_minutes}분 지연이 발생했습니다. 우회 경로를 확인해보세요.",
        alert_type="DELAY_ALERT",
        route_id=route_id,
        target_screen="route_detail",
        extra_data={
            "delayMinutes": str(delay_minutes),
        },
    )


def send_fcm_notification(notification: FcmNotification) -> dict[str, Any]:
    """
    FCM 알림 전송 함수 구조.

    실제 Firebase Admin SDK를 연결하면 이 함수 내부에서 메시지를 전송한다.
    현재는 GitHub 업로드용 코드이므로 payload를 반환하여 테스트 가능하게 작성하였다.
    """
    payload = notification.to_payload()

    # 실제 연동 시 예시:
    # import firebase_admin
    # from firebase_admin import messaging
    # message = messaging.Message(
    #     token=notification.token,
    #     notification=messaging.Notification(
    #         title=notification.title,
    #         body=notification.body,
    #     ),
    #     data=payload["message"]["data"],
    # )
    # response = messaging.send(message)
    # return {"success": True, "response": response}

    return {
        "success": True,
        "message": "FCM payload created successfully",
        "payload": payload,
    }


def send_week13_sample_notifications(token: str) -> list[dict[str, Any]]:
    """Week 13 테스트용 샘플 알림 전송 흐름"""
    notifications = [
        create_congestion_notification(
            token=token,
            route_id="8800",
            alternative_route="3007",
        ),
        create_departure_notification(
            token=token,
            route_id="8800",
            departure_time="07:41",
        ),
        create_alternative_route_notification(
            token=token,
            current_route="8800",
            alternative_route="3007",
        ),
        create_delay_notification(
            token=token,
            route_id="8800",
            delay_minutes=10,
        ),
    ]

    return [send_fcm_notification(notification) for notification in notifications]


if __name__ == "__main__":
    sample_token = "sample_fcm_token"
    results = send_week13_sample_notifications(sample_token)

    for index, result in enumerate(results, start=1):
        print(f"\n[Week 13 테스트 알림 {index}]")
        print(result)


# ---------------------------------------------------------------------------
# 12월 출발지 · 도착지 모드: last_trip 기준 돌발 알림 (docs/api.md ⑥ · '출발지 · 도착지 모드')
# notify.incident 가 true 인 사용자의 last_trip 으로 GET /api/v1/incidents?from_*&to_* 를 부르고,
# push 가 있으면 FCM 전송. 같은 사용자 · 같은 돌발은 30분 안에 다시 보내지 않음.
# push.data 의 from_* · to_* 를 그대로 넣어 알림을 누르면 B 대안 화면이 그 경로로 열림.
# ---------------------------------------------------------------------------

import json
import os
import time
import urllib.parse
import urllib.request

RESEND_SEC = 30 * 60
SENT_LOG = "fcm_sent_log.json"


def trip_query(last_trip: dict) -> str:
    """last_trip {from, to} → from_lat · from_lng · from_name · to_lat · to_lng · to_name"""
    f, t = last_trip["from"], last_trip["to"]
    return urllib.parse.urlencode({
        "from_lat": f["lat"], "from_lng": f["lng"], "from_name": f.get("name", "현재 위치"),
        "to_lat": t["lat"], "to_lng": t["lng"], "to_name": t.get("name", "도착지")})


def fetch_incidents(base_url: str, last_trip: dict, id_token: str | None = None) -> dict:
    req = urllib.request.Request(base_url.rstrip("/") + "/api/v1/incidents?" + trip_query(last_trip))
    if id_token:
        req.add_header("Authorization", "Bearer " + id_token)
    with urllib.request.urlopen(req, timeout=60) as res:
        return json.load(res)


def incident_key(uid: str, response: dict) -> str:
    """같은 돌발인지 — 사용자 + 돌발 목록(노선 · 내용)"""
    items = sorted(f"{i.get('route')}|{i.get('type')}|{i.get('message')}" for i in response.get("incidents", []))
    return uid + "#" + "##".join(items)


def build_incident_pushes(users: list[dict], fetch, sent: dict, now: float | None = None) -> list[dict]:
    """
    users: [{uid, fcm_token, notify: {incident}, last_trip: {from, to}}] (Firestore users 문서)
    fetch(last_trip) → /incidents 응답. sent: {key: 보낸 시각} (30분 중복 방지, 갱신됨)
    반환: FCM 메시지 payload 목록 (data 는 모두 문자열 — FCM 규칙)
    """
    now = time.time() if now is None else now
    out = []
    for u in users:
        trip = u.get("last_trip") or {}
        if not (u.get("notify") or {}).get("incident") or not u.get("fcm_token"):
            continue
        if not (isinstance(trip.get("from"), dict) and isinstance(trip.get("to"), dict)):
            continue
        r = fetch(trip)
        push = r.get("push")
        if not push:
            continue
        key = incident_key(u["uid"], r)
        if key in sent and now - sent[key] < RESEND_SEC:
            continue
        sent[key] = now
        data = {k: str(v) for k, v in (push.get("data") or {}).items()}
        # 서버가 빠뜨려도 B 화면이 열 수 있게 last_trip 으로 채움
        for side, prefix in (("from", "from_"), ("to", "to_")):
            p = trip[side]
            data.setdefault(prefix + "lat", str(p["lat"]))
            data.setdefault(prefix + "lng", str(p["lng"]))
            data.setdefault(prefix + "name", str(p.get("name", "")))
        data.setdefault("screen", "alternatives")
        out.append({"message": {"token": u["fcm_token"],
                                "notification": {"title": push["title"], "body": push["body"]},
                                "data": data}})
    return out


def run_incident_alerts(base_url: str, service_account: str = "serviceAccountKey.json", dry_run: bool = True):
    """Firestore users 를 읽어 보냄 (firebase_admin 필요). dry_run 이면 payload 만 출력."""
    import firebase_admin
    from firebase_admin import credentials, firestore, messaging
    if not firebase_admin._apps:
        firebase_admin.initialize_app(credentials.Certificate(service_account))
    users = [{"uid": d.id, **d.to_dict()} for d in firestore.client().collection("users").stream()]
    sent = json.load(open(SENT_LOG, encoding="utf-8")) if os.path.exists(SENT_LOG) else {}
    pushes = build_incident_pushes(users, lambda trip: fetch_incidents(base_url, trip), sent)
    for p in pushes:
        m = p["message"]
        if dry_run:
            print(json.dumps(p, ensure_ascii=False))
        else:
            messaging.send(messaging.Message(token=m["token"], data=m["data"],
                                             notification=messaging.Notification(**m["notification"])))
    json.dump(sent, open(SENT_LOG, "w", encoding="utf-8"))
    return pushes
