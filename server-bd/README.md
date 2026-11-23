# server-bd · B 실시간 대안 · D 탑승 피드백 API 서버 (13주차)

담당: 이창록 (B · D) · 브랜치 `feature/BD-week13-server`

팀 공통 FastAPI 서버가 아직 없어서, **B · D 화면이 쓰는 두 API 만** 따로 만든 작은 서버입니다.
저장소의 다른 파일(추천 로직 `live_recommend.py` 포함)은 고치지 않고 그대로 불러 씁니다.
팀 공통 서버가 생기면 `app.py` 의 두 엔드포인트를 그쪽으로 옮기면 됩니다 (형식은 docs/api.md 그대로).

| API | 화면 | 동작 |
|---|---|---|
| `GET /api/v1/alternatives?home_stop=…&route=…` | ③ B 실시간 대안 | `api_key.py` 가 있으면 `live_recommend.py` 로 지금 상황 계산, 없거나 실패하면 `docs/examples/alternatives.json`. 응답 헤더 `X-Data-Source: live` / `example` |
| `POST /api/v1/feedback` | ⑦ D 탑승 피드백 | 본문 검사 후 SQLite(`server-bd/feedback.db`)에 저장 → `201 {"id": "fb_…"}` |
| `GET /health` | — | `{"ok", "live"(실시간 계산 가능 여부), "auth"}` |

- **인증**: 모든 API 가 `Authorization: Bearer <Firebase ID 토큰>` 을 Firebase 로 확인합니다 (토큰 없음 · 만료 → 401).
  피드백의 `uid` 가 토큰 사용자와 다르면 403.
- **오류 형식**: `{"error": {"code", "message"}}` — `unauthorized` · `invalid_token` · `unknown_stop` · `unknown_route` ·
  `route_not_at_stop`(그 노선이 서지 않는 정류장) · `bad_date` · `uid_mismatch` · `bad_request`.
- 정류장 · 노선 목록은 `config.py` 와 같게 두었습니다 (`config.py` 는 `api_key.py` 가 있어야 읽혀서 따로 둠 — 테스트가 같은지 검사).

## 실행

```bash
pip install -r server-bd/requirements.txt

# 로컬 Firebase 에뮬레이터 토큰을 받을 때 (android-account/firebase 에서 에뮬레이터를 켠 뒤)
FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 uvicorn app:app --app-dir server-bd --host 0.0.0.0 --port 8000

# 실제 Firebase 프로젝트 토큰을 받을 때
FIREBASE_PROJECT_ID=<프로젝트 ID> uvicorn app:app --app-dir server-bd --host 0.0.0.0 --port 8000
```

| 환경 변수 | 뜻 |
|---|---|
| `FIREBASE_AUTH_EMULATOR_HOST` | 로컬 Firebase Auth 에뮬레이터 주소 (예: `127.0.0.1:9099`) |
| `FIREBASE_PROJECT_ID` | 토큰을 확인할 Firebase 프로젝트 (기본 `demo-seat-forecast` = 에뮬레이터) |
| `SEAT_AUTH=off` | 토큰을 확인하지 않음 — 내 컴퓨터에서 화면만 볼 때 (배포 금지) |
| `SEAT_LIVE=0` | `api_key.py` 가 있어도 실시간 계산을 끄고 예시 응답만 |
| `SEAT_FEEDBACK_DB` | 피드백 SQLite 파일 위치 (기본 `server-bd/feedback.db`, 저장소에 올리지 않음) |

안드로이드 에뮬레이터에서는 `http://10.0.2.2:8000/` 이 내 컴퓨터의 이 서버입니다 (B · D 시연 앱의 `SERVER_BASE_URL`).

## 테스트

```bash
python -m pytest server-bd -q                                                   # 22개
FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 python -m pytest server-bd -q       # + 에뮬레이터 실제 토큰 1개
```

| 테스트 | 확인하는 것 |
|---|---|
| 정류장 · 노선 | `config.py` 의 STOPS · ROUTES 와 같은지 |
| `/alternatives` | docs 예시와 같은 형식 · 값, 토큰 없음 · 만료 401, 잘못된 정류장 · 노선 · 서지 않는 정류장 · 빠진 값 400, 실시간 계산이 되면 `live`, 실패하면 예시로 대신 |
| `/feedback` | SQLite 저장 · 201 id, 확률 null · 못 탔어요, 로그인 없음 401, 남의 uid 403, 날짜 · 노선 · 정류장 · 확률 범위 · 메모 길이 · 빠진 값 400 |
| 인증 | `SEAT_AUTH=off`, 형식이 틀린 토큰 거절, 에뮬레이터에서 실제 가입한 토큰 확인 |

## 13주차에 확인한 것 (11/23)

로컬 Firebase 에뮬레이터 + 이 서버 + 안드로이드 에뮬레이터(Pixel 2 · API 35):

- **B**: 시연 앱 `demo_mode=server` → 에뮬레이터에서 받은 진짜 ID 토큰으로 `GET /api/v1/alternatives` 200, 화면에 대안 카드 · `예시 데이터` 표시 (이 컴퓨터엔 `api_key.py` 가 없어 예시 응답)
- **D**: 시연 앱에서 회원가입 → '탔어요' → Firestore 기록 → `POST /api/v1/feedback` 201 → SQLite 에 같은 uid 로 저장 → Firestore 기록에 `sent: true` · `server_id` 가 서버 id 와 같음
