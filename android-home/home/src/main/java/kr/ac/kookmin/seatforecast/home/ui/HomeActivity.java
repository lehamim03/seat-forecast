package kr.ac.kookmin.seatforecast.home.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import kr.ac.kookmin.seatforecast.home.R;

/** {@link HomeFragment} 를 화면 하나로 여는 Activity (시연 앱 · 하단 탭이 없는 경우용). */
public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_activity_home);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.home_container, new HomeFragment())
                    .commit();
        }
    }
}
