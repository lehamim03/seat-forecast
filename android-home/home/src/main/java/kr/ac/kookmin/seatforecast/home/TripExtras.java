package kr.ac.kookmin.seatforecast.home;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 화면 사이 약속 (A · B · C · D · E 공통) — 출발지 · 도착지를 인텐트 extra 6개로 넘깁니다.
 * <pre>
 * from_lat · from_lng · to_lat · to_lng : double
 * from_name · to_name                   : String ("현재 위치" · "국민대학교" 등, 화면 표시용)
 * </pre>
 * 이름은 서버 요청 값(docs/api.md)과 같습니다. 팀 앱이 추천 카드에서 B 대안 화면을 열 때:
 * <pre>
 * Intent i = AlternativesActivity.newIntent(context, ...);
 * TripExtras.put(i, from, to);
 * </pre>
 */
public final class TripExtras {

    public static final String FROM_LAT = "from_lat";
    public static final String FROM_LNG = "from_lng";
    public static final String FROM_NAME = "from_name";
    public static final String TO_LAT = "to_lat";
    public static final String TO_LNG = "to_lng";
    public static final String TO_NAME = "to_name";

    private TripExtras() {
    }

    public static Intent put(@NonNull Intent intent, @NonNull Place from, @NonNull Place to) {
        return intent.putExtra(FROM_LAT, from.lat)
                .putExtra(FROM_LNG, from.lng)
                .putExtra(FROM_NAME, from.name)
                .putExtra(TO_LAT, to.lat)
                .putExtra(TO_LNG, to.lng)
                .putExtra(TO_NAME, to.name);
    }

    /** 출발지 extra 가 없으면 null */
    @Nullable
    public static Place from(@Nullable Intent intent) {
        return read(intent, FROM_LAT, FROM_LNG, FROM_NAME, Place.CURRENT_LOCATION);
    }

    /** 도착지 extra 가 없으면 null */
    @Nullable
    public static Place to(@Nullable Intent intent) {
        return read(intent, TO_LAT, TO_LNG, TO_NAME, "도착지");
    }

    @Nullable
    private static Place read(@Nullable Intent i, String lat, String lng, String name, String fallback) {
        if (i == null || !i.hasExtra(lat) || !i.hasExtra(lng)) return null;
        String n = i.getStringExtra(name);
        return new Place(n == null || n.isEmpty() ? fallback : n,
                i.getDoubleExtra(lat, Double.NaN), i.getDoubleExtra(lng, Double.NaN));
    }
}
