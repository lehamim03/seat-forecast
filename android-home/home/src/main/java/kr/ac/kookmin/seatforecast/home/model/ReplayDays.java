package kr.ac.kookmin.seatforecast.home.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/** {@code GET /api/v1/replay/days} → {@code {"days": ["2026-10-07", …]}} */
public class ReplayDays {

    @SerializedName("days")
    public List<String> days = new ArrayList<>();
}
