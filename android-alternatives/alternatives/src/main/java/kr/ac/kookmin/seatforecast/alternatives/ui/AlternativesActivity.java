package kr.ac.kookmin.seatforecast.alternatives.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import kr.ac.kookmin.seatforecast.alternatives.Place;
import kr.ac.kookmin.seatforecast.alternatives.R;
import kr.ac.kookmin.seatforecast.alternatives.TripExtras;

/**
 * {@link AlternativesFragment} 를 화면 하나로 여는 Activity — A 홈 추천 카드 · E 돌발 알림 푸시에서 엽니다.
 * 출발지 · 도착지는 extra 6개 ({@link TripExtras}: from_lat · from_lng · from_name · to_lat · to_lng · to_name).
 * <pre>
 * startActivity(AlternativesActivity.newIntent(this, from, to));   // A 홈 카드 (from = null 이면 현재 위치)
 * startActivity(AlternativesActivity.newIntent(this));             // 현재 GPS + 마지막 도착지
 * // E 푸시: push.data 의 from_* · to_* 를 그대로 extra 로 넣어도 됨
 * </pre>
 */
public class AlternativesActivity extends AppCompatActivity {

    public static Intent newIntent(Context context) {
        return new Intent(context, AlternativesActivity.class);
    }

    /** @param from null 이면 현재 위치(GPS), to null 이면 마지막 도착지 */
    public static Intent newIntent(Context context, @Nullable Place from, @Nullable Place to) {
        return TripExtras.put(newIntent(context), from, to);
    }

    /**
     * 지난 출근길 재생 — A 홈이 재생 중일 때 카드에서 엶. replay 가 있으면 from · to 대신 replay 로 요청합니다.
     *
     * @param replayAt 예: "2026-11-11T08:20" (null 이면 실시간)
     */
    public static Intent newIntent(Context context, @Nullable Place from, @Nullable Place to, @Nullable String replayAt) {
        Intent i = newIntent(context, from, to);
        if (replayAt != null) i.putExtra(TripExtras.REPLAY, replayAt);
        return i;
    }

    /** 예전 방식 (정류장 · 노선) — 출발지 · 도착지 모드에서는 쓰지 않아 현재 위치 + 마지막 도착지로 엽니다. */
    @Deprecated
    public static Intent newIntent(Context context, @Nullable String homeStopId, @Nullable String route) {
        return newIntent(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.alt_activity_alternatives);
        setTitle(R.string.alt_title);
        ActionBar bar = getSupportActionBar();
        if (bar != null && !isTaskRoot()) bar.setDisplayHomeAsUpEnabled(true);
        if (savedInstanceState == null) {
            Bundle extras = getIntent().getExtras();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.alt_container, AlternativesFragment.newInstance(
                            TripExtras.from(extras), TripExtras.to(extras), TripExtras.replay(extras)))
                    .commit();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
