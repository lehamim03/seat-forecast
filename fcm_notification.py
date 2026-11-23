"""
좌석예보 FCM 알림 payload 생성 모듈

E-notification 담당 기능:
- 좌석예보 앱의 돌발 알림 푸시 메시지 구조 정리
- 만석 위험, 출발 시각, 대체 경로 안내 알림 payload 생성
- 추후 Firebase Cloud Messaging 서버 연동 시 사용할 기본 구조 준비
"""

from __future__ import annotations

from typing import Any


NotificationData = dict[str, str]
FcmPayload = dict[str, Any]


def create_fcm_payload(
    token: str,
    title: str,
    body: str,
    data: NotificationData | None = None,
) -> FcmPayload:
    """
    FCM 전송에 사용할 기본 payload를 생성한다.

    Args:
        token: 사용자 기기의 FCM Token
        title: 알림 제목
        body: 알림 내용
        data: 앱에서 추가로 사용할 데이터

    Returns:
        FCM HTTP v1 API 형식의 메시지 payload
    """
    return {
        "message": {
            "token": token,
            "notification": {
                "title": title,
                "body": body,
            },
            "data": data or {},
        }
    }


def create_congestion_alert(
    token: str,
    route_id: str,
    alternative_route: str,
) -> FcmPayload:
    """
    만석 위험 / 혼잡 알림 payload를 생성한다.
    """
    title = "좌석예보"
    body = (
        f"{route_id}번 버스 만석 위험이 높아졌습니다. "
        f"{alternative_route}번 대체 경로를 확인해보세요."
    )

    data = {
        "type": "CONGESTION_ALERT",
        "routeId": route_id,
        "alternativeRoute": alternative_route,
        "targetScreen": "alternative_route",
    }

    return create_fcm_payload(token, title, body, data)


def create_departure_alert(
    token: str,
    route_id: str,
    departure_time: str,
) -> FcmPayload:
    """
    출발 시각 알림 payload를 생성한다.
    """
    title = "좌석예보"
    body = f"지금 출발하면 {departure_time} {route_id}번 버스를 탈 가능성이 높습니다."

    data = {
        "type": "DEPARTURE_ALERT",
        "routeId": route_id,
        "departureTime": departure_time,
        "targetScreen": "recommended_route",
    }

    return create_fcm_payload(token, title, body, data)


def create_alternative_route_alert(
    token: str,
    current_route: str,
    alternative_route: str,
) -> FcmPayload:
    """
    대체 경로 추천 알림 payload를 생성한다.
    """
    title = "좌석예보"
    body = (
        f"현재 {current_route}번 경로의 탑승 성공 확률이 낮습니다. "
        f"{alternative_route}번 노선을 추천합니다."
    )

    data = {
        "type": "ALTERNATIVE_ROUTE_ALERT",
        "currentRoute": current_route,
        "alternativeRoute": alternative_route,
        "targetScreen": "alternative_route",
    }

    return create_fcm_payload(token, title, body, data)


def create_delay_alert(
    token: str,
    route_id: str,
    delay_minutes: int,
) -> FcmPayload:
    """
    지연 / 돌발 정체 알림 payload를 생성한다.
    """
    title = "좌석예보"
    body = f"{route_id}번 버스가 약 {delay_minutes}분 지연되고 있습니다. 이동 시간을 다시 확인해보세요."

    data = {
        "type": "DELAY_ALERT",
        "routeId": route_id,
        "delayMinutes": str(delay_minutes),
        "targetScreen": "route_detail",
    }

    return create_fcm_payload(token, title, body, data)


def create_sample_payloads(token: str = "sample_fcm_token") -> list[FcmPayload]:
    """
    테스트용 알림 payload 목록을 생성한다.
    """
    return [
        create_congestion_alert(
            token=token,
            route_id="8800",
            alternative_route="3007",
        ),
        create_departure_alert(
            token=token,
            route_id="8800",
            departure_time="07:41",
        ),
        create_alternative_route_alert(
            token=token,
            current_route="8800",
            alternative_route="3007",
        ),
        create_delay_alert(
            token=token,
            route_id="8800",
            delay_minutes=10,
        ),
    ]


if __name__ == "__main__":
    for index, payload in enumerate(create_sample_payloads(), start=1):
        print(f"\n[{index}] FCM 알림 payload")
        print(payload)
