# 좌석예보 API 형식 (앱 ↔ 예측 서버)

앱(Android · Java)과 예측 서버(FastAPI · Python)가 주고받는 데이터 형식입니다.
화면별 예시 응답은 [`docs/examples/`](examples/)에 있습니다. 서버가 완성되기 전에는 이 파일로 화면을 먼저 만들면 됩니다.

- **서버**: 저장소 루트의 `server.py` (실행 방법은 [README의 "예측 서버"](../README.md#예측-서버-앱이-부르는-api)). 에뮬레이터에서는 `http://10.0.2.2:8000/` 입니다. 서버 상태는 `GET /health`.

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
| 오류 | HTTP 상태 코드 + `{"error": {"code": "...", "message": "..."}}` — `message` 는 화면에 그대로 보여줘도 되는 문장 |
| 오류 코드 | `400` `bad_request`(요청 값 형식) · `unknown_stop` · `unknown_route` / `401` `unauthorized`(토큰 없음 · 만료) / `404` `not_found` / `500` `internal` |
| 요청 값 기본값 | `home_stop` · `route` 를 빼면 `203000063` · `8800` 으로 계산 (정류장 3곳 · 노선 4개만 지원) |

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

## 출발지 · 도착지 모드 (지도 앱처럼 — 12월부터 앱 기본)

집 → 국민대 한 경로가 아니라, **출발지(기본: 현재 GPS)와 도착지를 정해** 계산합니다. 서버: `trip.py`.
②③④⑤⑥ 의 GET 에 아래 요청 값을 붙이면 이 모드로 답합니다 (붙이지 않으면 예전 `home_stop` · `route` 방식).

| 요청 값 | 예 | 설명 |
|---|---|---|
| `from_lat`, `from_lng` | `37.3850`, `127.1233` | 출발지 좌표 — 현재 GPS 또는 검색한 곳 (필수) |
| `from_name` | `현재 위치` | 화면 표시용 (기본 "현재 위치") |
| `to_lat`, `to_lng` | `37.6110`, `126.9952` | 도착지 좌표 (필수) |
| `to_name` | `국민대학교` | 화면 표시용 (기본 "도착지") |

계산: ODsay 로 출발지 → 도착지 경로 후보를 받고, 첫 탈것이 버스면 그 정류장의 실시간 도착정보(남은 정류장 · 지금 좌석)로 탑승 확률과 기대 탑승 시각을 구해 도착 예상 순으로 정렬합니다. 학습 데이터가 없는 노선이라 **노선 번호 없이 학습한 공통 모델**을 씁니다 (처음 보는 노선 · 정류장 검증: `generalize.py`).

응답은 ②③④⑤⑥ 의 기존 형식을 그대로 쓰고 아래만 더합니다. 예시: [trip_recommendations.json](examples/trip_recommendations.json) · [trip_alternatives.json](examples/trip_alternatives.json) · [trip_map.geojson](examples/trip_map.geojson) (가짜 ODsay 응답으로 만든 예시)

| 필드 | 설명 |
|---|---|
| `origin`, `destination` | `{name, lat, lng}` — 요청한 출발지 · 도착지 |
| `my_route` | 항상 `null` |
| `route_source` | `odsay` = 이 출발지로 조회한 경로 · `saved_nearby` = ODsay 를 못 써서(하루 한도 · IP) 1.5km 안에 저장된 출발지의 경로를 쓰고 도보만 다시 계산 (화면에 "근처 저장 경로 기준" 정도로 표시) |
| `home_stop` | 1순위 선택지의 탈 정류장 `{id, name}` |
| option `stop` | `{id, name, mobile_no, lat, lng}` — 지하철이면 역 |
| option `walk_min` | 출발지에서 탈 곳까지 걷는 분 |
| option `first_mode` | 처음 타는 것 `bus` · `subway` |
| option `seat_limited` | `true` = 직행좌석 · 좌석 · 광역급행이라 만석이면 못 탐 (탑승 확률이 의미 있는 경우). `false` = 서서 탈 수 있음 · 지하철 → `board_prob` 1.0, 화면엔 "입석 가능" |
| option `seat_known` | `seat_limited` 인데 좌석 수를 알면 `true`. `false` 면 (서울 등록 노선 등) 평소 탑승률(출퇴근 93% · 그 외 98%)로 계산한 값이라 화면엔 확률 대신 "좌석 정보 없음" |
| option `kind` | `기다리기`(가장 가까운 탈 곳) · `걸어가기`(그 노선을 더 먼 정류장에서) · `다른 버스` · **`지하철`** |
| option `transfers`, `summary` | 갈아타는 횟수 · 출발지 → 도착지 전체 경로 한 줄 |
| option `route_choices` | 그 경로 하나 (`tags`: 추천 · 최단시간 · 최소환승) |
| ③ `subway` | 지하철로 시작하는 가장 빠른 경로 (없으면 `null`) |
| ④ 지도 | `me` = 출발지, **`destination`** 점 추가, `transfer` 선 = 상위 경로 4개 (지나는 정류장 좌표), `incident` = 경로 근처 공식 돌발 |
| ⑥ `push.data` | `from_lat` · `from_lng` · `from_name` · `to_lat` · `to_lng` · `to_name` 포함 — 알림을 누르면 이 출발지 · 도착지로 대안 화면 |
| 오류 | `400 bad_request` 좌표 일부만 줌 · `503 route_unavailable` 경로 조회 실패 (ODsay IP 미등록 · 한도) |

**장소 검색** — `GET /api/v1/places?q=국민대` → `{"query", "places": [{"name", "address", "lat", "lng"}]}` (TMAP, 1시간 저장). 예시: [trip_places.json](examples/trip_places.json)

**마이페이지 (Firestore `users/{uid}`)** — 이 모드에서 쓰는 필드: `favorites` `[{name, lat, lng}]`(집 · 학교 등), `destination` `{name, lat, lng}`(기본 · 최근 도착지), `last_trip` `{from: {name, lat, lng}, to: {name, lat, lng}, at}`(돌발 알림 대상). `home_stop_id` · `my_route` 는 선택.

**탑승 피드백** — 이 모드에서는 어떤 `route` · `stop_id` 든 받습니다.

---

## 지난 출근길 재생 (발표 · 시연용)

수집한 기록(data/)으로 **그날 그 시각의 추천을 다시 만들고, 실제로 어떻게 됐는지** 함께 줍니다. 서버: `replay.py`. 집 → 국민대 경로(정류장 3곳 · 노선 4개)만 됩니다.

- `GET /api/v1/recommendations?replay=2026-11-12T07:40` — `alternatives` · `incidents` 도 같은 요청 값 (`from_*` · `to_*` 와 함께 쓰면 400)
- `GET /api/v1/replay/days` → `{"days": ["2026-10-07", …]}` 재생할 수 있는 운행일
- 응답: ② · ③ · ⑥ 형식 그대로 + 최상위 `replay` `{at, date, message, note}` + 선택지마다 `actual`

| 필드 | 설명 |
|---|---|
| `replay.message` | 화면에 그대로: "그날 1순위(8800번 · 삼성1차아파트)대로 했다면 07:51에 타서 09:44 국민대 도착 · 평소대로면 …" |
| option `actual.boarded_at` | 그 정류장에 갔다면 실제로 탄 버스의 출발 시각 (모두 만석이면 `null`) |
| option `actual.full_skipped` | 만석으로 보낸 버스 수 |
| option `actual.arrive_at`, `actual.total_min` | 실제 서울 도착 기록 기준 국민대 도착 · 걸린 분 |
| 오류 | `400 replay_no_data` 그날 기록 없음 · 수집 공백 시각 |

한계: 그때의 정확한 도착 예정(분)은 기록이 없어 정류장 수로 추정하고, 공식 돌발정보는 지난 것을 받을 수 없어 뺍니다.

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
| `subway` | 지하철 경로 비교 `{arrive_at, total_min, transfers, walk_min, summary}` — 조회 실패면 `null` |

화면: `recommendation.kind`와 같은 카드에 "추천" 표시, 각 카드에 `arrive_at` · `buses[0]` 정보.

## ④ 지도 — `GET /api/v1/map`

카카오맵에 그릴 GeoJSON(`FeatureCollection`)입니다. 요청 값 `home_stop`(선택, `me` 위치와 도보선 기준). 좌표는 `[경도, 위도]` 순서입니다. `properties.kind`로 무엇인지 구분합니다.

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
| `push` | FCM 알림 — `title`, `body`, `data.screen`(눌렀을 때 열 화면: `alternatives`), `data.route`. 평소 노선에 돌발이 없으면 `null` |

요청 값은 ②와 같습니다. 돌발이 없을 때 `incidents` 는 빈 목록입니다.

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
