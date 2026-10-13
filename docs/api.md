# 좌석예보 API 형식 (앱 ↔ 예측 서버)

앱(Android · Java)과 예측 서버(FastAPI · Python)가 주고받는 데이터 형식입니다.
화면별 예시 응답은 [`docs/examples/`](examples/)에 있습니다. 서버가 완성되기 전에는 이 파일로 화면을 먼저 만들면 됩니다.

- 예시 파일은 `python api_examples.py`로 다시 만들 수 있습니다. 가상 상황(평일 07:40, 평소 삼성1차아파트에서 8800)을 실제 추천 로직(`live_recommend.py`)으로 계산한 결과입니다.
- 형식을 바꿀 때는 이 문서와 예시 파일을 함께 고치고 팀 전체에 알립니다.

## 공통 규칙

| 항목 | 규칙 |
|---|---|
| 주소 | `https://<서버>/api/v1/...` |
| 인증 | 헤더 `Authorization: Bearer <Firebase ID 토큰>` — 서버가 Firebase로 확인 |
| 형식 | JSON, UTF-8. 시각은 한국 시간 `"HH:MM"`, 날짜·시각 전체는 `"2026-10-07T07:40:00+09:00"` |
| 정류장 이름 | 공공데이터 API 이름 그대로 (줄이거나 바꾸지 않음). 예: `매탄위브하늘채.경기아트센터`, `양재역.서초문화예술회관(중)` |
| 정류장 ID | 경기도 정류소 ID (`203000063` 등). 화면에 보이는 5자리 번호는 `mobile_no` (`04007`) |
| 확률 | `0.0 ~ 1.0` (화면에는 `%`로) — 70% 이상 초록, 40~70% 노랑, 40% 미만 빨강 |
| 오류 | HTTP 상태 코드 + `{"error": {"code": "...", "message": "..."}}` |

## 화면별 API 한눈에

| 화면 | 메서드 · 주소 | 예시 파일 |
|---|---|---|
| ① 로그인 · 회원가입 | Firebase Auth (앱에서 직접) | — |
| ② 홈 — 정류장 × 노선 추천 | `GET /api/v1/recommendations` | [recommendations.json](examples/recommendations.json) |
| ③ 실시간 대안 | `GET /api/v1/alternatives` | [alternatives.json](examples/alternatives.json) |
| ④ 지도 | `GET /api/v1/map` | [map.geojson](examples/map.geojson) |
| ⑤ 경로 여러 개 | `GET /api/v1/route-choices` | [route_choices.json](examples/route_choices.json) |
| ⑥ 돌발 알림 | `GET /api/v1/incidents` + FCM 푸시 | [incidents.json](examples/incidents.json) |
| ⑦ 마이페이지 | Firestore `users/{uid}` (앱에서 직접) | [settings.json](examples/settings.json) |
| ⑦ 탑승 피드백 | `POST /api/v1/feedback` | [feedback.json](examples/feedback.json) |

---

## ② 홈 — `GET /api/v1/recommendations`

지금 출발하면 어느 정류장에서 어떤 버스를 타야 가장 빨리 도착하는지, 정류장 × 노선 조합의 순위입니다.

| 요청 값 | 예 | 설명 |
|---|---|---|
| `home_stop` | `203000063` | 평소 타는 정류장 (마이페이지 설정) |
| `route` | `8800` | 평소 타는 노선 |

응답:

| 필드 | 설명 |
|---|---|
| `generated_at` | 계산한 시각 |
| `my_route`, `home_stop` | 요청한 평소 노선 · 정류장 |
| `day` | `label`(화면 문구, 예: "평일 출퇴근 시간대"), `kind`, `drop_factor`, `headway_factor` |
| `recommendation` | 1순위 요약 — `kind`, `route`, `stop_name`, `message`(화면에 그대로 보여줄 문장) |
| `ranking[]` | 도착 예상 시각 순 조합 목록 — 아래 **선택지(option)** 형식 |

**선택지(option)** — ②·③·⑥에서 공통으로 씁니다.

| 필드 | 예 | 설명 |
|---|---|---|
| `kind` | `"다른 버스"` | `기다리기` · `걸어가기` · `다른 버스` |
| `route` | `"3007"` | 탈 노선 |
| `stop` | `{"id","name","mobile_no"}` | 탈 정류장 |
| `walk_min` | `3` | 평소 정류장에서 걸어가는 시간(분) |
| `board_prob` | `1.0` | 걸어가서 탈 수 있는 첫 차의 탑승 확률 |
| `arrive_at` | `"10:08"` | 국민대 도착 예상 (`skipped`면 `null`) |
| `total_min` | `148` | 지금부터 국민대까지 예상 시간(분) |
| `delay_min` | `0` | 돌발 · 행사로 더한 지연(분) |
| `skipped` | `false` | 우회로 이 정류장을 건너뛰는 중이면 `true` (탈 수 없음) |
| `alight_stop` | `"양재역.서초문화예술회관(중)"` | 서울에서 내릴 정류장 (자동 선택) |
| `buses[]` | | 다가오는 차량 — `order`, `eta_min`(도착까지 분), `seats_at_stop`(도착 때 예상 좌석, 모르면 `null`), `board_prob`, `reachable`(걸어가서 탈 수 있는지) |
| `route_choices[]` | | 서울에서 국민대까지 경로 여러 개 — ⑤ 형식 + `arrive_at` |

## ③ 실시간 대안 — `GET /api/v1/alternatives`

평소 버스가 만석일 때 세 가지 선택지를 비교합니다. 요청 값은 ②와 같습니다.

| 필드 | 설명 |
|---|---|
| `generated_at`, `my_route`, `home_stop`, `day`, `recommendation` | ②와 같음 |
| `alternatives` | `{"기다리기": option, "걸어가기": option, "다른 버스": option}` — 해당 없으면 `null` |

화면: `recommendation.kind`와 같은 카드에 "추천" 표시, 각 카드에 `arrive_at` · `buses[0]` 정보.

## ④ 지도 — `GET /api/v1/map`

카카오맵에 그릴 GeoJSON(`FeatureCollection`)입니다. 좌표는 `[경도, 위도]` 순서입니다. `properties.kind`로 무엇인지 구분합니다.

| `kind` | 도형 | 속성 | 그리는 법 |
|---|---|---|---|
| `me` | 점 | `name` | 파란 점 (앱에서는 GPS 위치로 대체) |
| `stop` | 점 | `name`, `mobile_no`, `stop_id` | 정류장 마커 |
| `walk` | 선 | `to`, `minutes` | 파란 점선 (TMAP 보행자 경로) |
| `route` | 선 | `route`, `dest`(하차 정류장), `color` | 노선 색 선 |
| `bus` | 점 | `route`, `plate`, `seq`, `station`, `remain_seat`, `color` | 버스 아이콘 + 잔여 좌석 |
| `incident` | 점 | `type`, `road`, `message` | 노란 경고 마커 |
| `transfer` | 선 | `route`, `main`, `tags`, `minutes`, `transfers`, `summary` | `main: true` = 추천 경로(굵은 보라), 나머지는 회색 점선 |

## ⑤ 경로 여러 개 — `GET /api/v1/route-choices`

노선마다 서울에서 내린 뒤 국민대까지 경로를 최대 4개 보여줍니다. ②·③의 `route_choices`도 같은 형식입니다.

| 필드 | 설명 |
|---|---|
| `routes[].route` | 노선 |
| `routes[].alight_stop` | 추천 경로의 하차 정류장 |
| `routes[].minutes_from_seoul_first_stop` | 서울 첫 정차 정류장부터 국민대까지 예상(분) |
| `routes[].choices[]` | `minutes`, `transfers`(광역버스에서 내린 뒤 갈아타는 수단 수), `walk`(걷기 분), `seq`, `from`(하차 정류장), `transfer`(경로 요약), `tags` |

`tags`: `추천`(체감 시간 최소 — 걷기 · 기다림 1.5배, 환승 1회당 +5분, 출근 시간 시내버스 1.2배) · `최단시간`(도착 가장 빠름) · `최소환승`. 한 경로에 여러 개 붙을 수 있고, 목록 첫 번째가 항상 `추천`입니다.

## ⑥ 돌발 알림 — `GET /api/v1/incidents` + 푸시

| 필드 | 설명 |
|---|---|
| `incidents[]` | `route`, `delay_min`, `skip_stops`(건너뛰는 정류장 ID), `drop_factor`(좌석이 평소의 몇 배로 주는지), `notes`(화면 문구) |
| `recommendation`, `alternatives` | 돌발을 반영한 ③과 같은 형식 |
| `push` | FCM 알림 — `title`, `body`, `data.screen`(눌렀을 때 열 화면: `alternatives`), `data.route` |

서버가 돌발을 감지하면 해당 노선을 평소 노선으로 둔 사용자에게 `push` 내용을 FCM으로 보냅니다.

## ⑦ 마이페이지 — Firestore `users/{uid}`

앱이 Firestore에 직접 읽고 씁니다(서버를 거치지 않음). 예: [settings.json](examples/settings.json)

| 필드 | 설명 |
|---|---|
| `home_stop_id` | 평소 정류장 |
| `candidate_stop_ids` | 비교할 주변 정류장 |
| `my_route` | 평소 노선 |
| `destination` | 목적지 이름 · 좌표 |
| `arrive_by` | 도착 목표 시각 |
| `notify` | `departure`(출발 알림), `incident`(돌발 알림), `minutes_before` |

## ⑦ 탑승 피드백 — `POST /api/v1/feedback`

탄 뒤 "탔어요 / 못 탔어요"를 보내면 예측 검증과 재학습에 씁니다. 예: [feedback.json](examples/feedback.json)

| 필드 | 설명 |
|---|---|
| `date`, `route`, `stop_id` | 언제 · 어느 노선 · 어느 정류장 |
| `boarded` | 탔으면 `true` |
| `predicted_prob` | 그때 앱이 보여준 탑승 확률 |
| `memo` | 선택 입력 |

응답: `201 Created` + `{"id": "..."}`
