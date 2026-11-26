package kr.ac.kookmin.seatforecast.app;

import androidx.annotation.Nullable;

/**
 * 이 앱 실행 중 마지막으로 검색한 출발지 · 도착지 (A 홈 saveLastTrip 때 갱신).
 * 실시간 대안 탭 · 지도 탭을 extra 없이 열 때 이 값을 씁니다. 로그인했으면 D 의 last_trip 에도 저장됩니다.
 *
 * <p>모듈마다 Place 클래스가 따로 있어(모양은 {name, lat, lng} 로 같음) 여기서 바꿔 줍니다.
 */
final class LastTrip {

    private static kr.ac.kookmin.seatforecast.home.Place from;
    private static kr.ac.kookmin.seatforecast.home.Place to;
    /** 바뀔 때마다 1 씩 — 대안 탭이 예전 경로로 만들어졌는지 확인 */
    private static int version;

    private LastTrip() {
    }

    static synchronized void set(kr.ac.kookmin.seatforecast.home.Place f, kr.ac.kookmin.seatforecast.home.Place t) {
        if (f == null || t == null) return;
        if (from != null && to != null && from.key().equals(f.key()) && to.key().equals(t.key())) return;
        from = f;
        to = t;
        version++;
        kr.ac.kookmin.seatforecast.map.MapServer.setLastTrip(toMap(f), toMap(t));
    }

    static synchronized int version() {
        return version;
    }

    @Nullable
    static synchronized kr.ac.kookmin.seatforecast.alternatives.Place altFrom() {
        return from == null ? null : toAlt(from);
    }

    @Nullable
    static synchronized kr.ac.kookmin.seatforecast.alternatives.Place altTo() {
        return to == null ? null : toAlt(to);
    }

    // ── 모듈 사이 Place 바꾸기 ──

    static kr.ac.kookmin.seatforecast.alternatives.Place toAlt(kr.ac.kookmin.seatforecast.home.Place p) {
        return new kr.ac.kookmin.seatforecast.alternatives.Place(p.name, p.lat, p.lng);
    }

    static kr.ac.kookmin.seatforecast.map.Place toMap(kr.ac.kookmin.seatforecast.home.Place p) {
        return new kr.ac.kookmin.seatforecast.map.Place(p.name, p.lat, p.lng);
    }

    static kr.ac.kookmin.seatforecast.account.Place toAccount(kr.ac.kookmin.seatforecast.home.Place p) {
        return new kr.ac.kookmin.seatforecast.account.Place(p.name, p.lat, p.lng);
    }

    @Nullable
    static kr.ac.kookmin.seatforecast.home.Place toHome(@Nullable kr.ac.kookmin.seatforecast.account.Place p) {
        return p == null ? null : new kr.ac.kookmin.seatforecast.home.Place(p.name, p.address, p.lat, p.lng);
    }

    @Nullable
    static kr.ac.kookmin.seatforecast.alternatives.Place toAlt(@Nullable kr.ac.kookmin.seatforecast.account.Place p) {
        return p == null ? null : new kr.ac.kookmin.seatforecast.alternatives.Place(p.name, p.lat, p.lng);
    }
}
