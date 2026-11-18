package kr.ac.kookmin.seatforecast.alternatives.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import kr.ac.kookmin.seatforecast.alternatives.R;

/**
 * {@link AlternativesFragment} 를 화면 하나로 여는 Activity — A 홈 추천 카드 · E 돌발 알림 푸시에서 엽니다.
 * <pre>
 * startActivity(AlternativesActivity.newIntent(this));                          // 평소 정류장 · 노선
 * startActivity(AlternativesActivity.newIntent(this, "203000063", "8800"));     // 정류장 · 노선 지정
 * </pre>
 */
public class AlternativesActivity extends AppCompatActivity {

    public static final String EXTRA_HOME_STOP = "kr.ac.kookmin.seatforecast.alternatives.HOME_STOP";
    public static final String EXTRA_ROUTE = "kr.ac.kookmin.seatforecast.alternatives.ROUTE";

    public static Intent newIntent(Context context) {
        return new Intent(context, AlternativesActivity.class);
    }

    public static Intent newIntent(Context context, @Nullable String homeStopId, @Nullable String route) {
        return newIntent(context).putExtra(EXTRA_HOME_STOP, homeStopId).putExtra(EXTRA_ROUTE, route);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.alt_activity_alternatives);
        setTitle(R.string.alt_title);
        ActionBar bar = getSupportActionBar();
        if (bar != null && !isTaskRoot()) bar.setDisplayHomeAsUpEnabled(true);
        if (savedInstanceState == null) {
            Intent i = getIntent();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.alt_container, AlternativesFragment.newInstance(
                            i.getStringExtra(EXTRA_HOME_STOP), i.getStringExtra(EXTRA_ROUTE)))
                    .commit();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
