package kr.ac.kookmin.seatforecast.alternatives.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.List;

import kr.ac.kookmin.seatforecast.alternatives.R;
import kr.ac.kookmin.seatforecast.alternatives.model.Bus;
import kr.ac.kookmin.seatforecast.alternatives.model.Option;
import kr.ac.kookmin.seatforecast.alternatives.model.RouteChoice;

/** 대안 카드 한 장 (alt_item_option) 을 만들고 채웁니다. 규칙은 {@link AltFormat}. */
final class OptionCard {

    private static final String[] NUMBERS = {"", "①", "②", "③"};

    private OptionCard() {
    }

    /**
     * @param all         화면의 대안 전부 (가장 빠른 대안과 비교용)
     * @param recommended '추천' 카드인지
     */
    static View create(LayoutInflater inflater, ViewGroup parent, Option o, List<Option> all,
                       boolean recommended, @Nullable View.OnClickListener onClick) {
        Context c = parent.getContext();
        View v = inflater.inflate(R.layout.alt_item_option, parent, false);
        v.setBackgroundResource(recommended ? R.drawable.alt_bg_option_recommended : R.drawable.alt_bg_option);
        v.setOnClickListener(onClick);
        v.setClickable(onClick != null);

        int number = AltFormat.number(o);
        String kind = o.kind == null ? "" : o.kind;
        TextView title = v.findViewById(R.id.alt_option_title);
        title.setText(number == 0 ? kind : c.getString(R.string.alt_card_title, NUMBERS[number], kind));
        v.findViewById(R.id.alt_option_tag).setVisibility(recommended ? View.VISIBLE : View.GONE);

        TextView badge = v.findViewById(R.id.alt_option_badge);
        AltFormat.Level level = AltFormat.level(o.boardProb);
        switch (AltFormat.probLabel(o)) {
            case STANDING:   // seat_limited=false
                badge.setText(R.string.alt_standing);
                badge.setBackgroundResource(AltStyle.pill(AltFormat.Level.HIGH));
                badge.setTextColor(color(c, R.color.alt_high));
                break;
            case SEAT_UNKNOWN:   // seat_known=false
                badge.setText(R.string.alt_seat_unknown);
                badge.setBackgroundResource(AltStyle.pill(AltFormat.Status.UNKNOWN));
                badge.setTextColor(color(c, R.color.alt_muted));
                break;
            default:
                badge.setText(AltFormat.percent(o.boardProb));
                badge.setBackgroundResource(AltStyle.pill(level));
                badge.setTextColor(color(c, AltStyle.color(level)));
        }
        badge.setVisibility(o.skipped ? View.GONE : View.VISIBLE);

        ((TextView) v.findViewById(R.id.alt_option_route)).setText(o.route);
        ((TextView) v.findViewById(R.id.alt_option_arrive)).setText(o.arriveAt == null
                ? c.getString(R.string.alt_arrive_none) : c.getString(R.string.alt_arrive, o.arriveAt));

        TextView compare = v.findViewById(R.id.alt_option_compare);
        Integer slower = AltFormat.slowerThanFastest(o, all);
        if (slower == null || all.size() < 2) {
            compare.setVisibility(View.GONE);
        } else if (slower <= 0) {
            compare.setText(R.string.alt_fastest);
            compare.setTextColor(color(c, R.color.alt_high));
        } else {
            compare.setText(c.getString(R.string.alt_slower, slower));
            compare.setTextColor(color(c, R.color.alt_muted));
        }

        ((TextView) v.findViewById(R.id.alt_option_plan)).setText(plan(c, o));

        RouteChoice main = o.mainChoice();
        String alight = o.alightStop != null ? o.alightStop : main == null ? null : main.alightStop;
        TextView seoul = v.findViewById(R.id.alt_option_seoul);
        if (o.summary != null && !o.summary.trim().isEmpty()) {
            // 출발지 · 도착지 모드: 출발지 → 도착지 전체 경로 한 줄
            seoul.setText(c.getString(R.string.alt_route_line, o.summary.trim()));
        } else if (alight == null) {
            v.findViewById(R.id.alt_option_seoul_row).setVisibility(View.GONE);
        } else if (main != null && main.summary != null && !main.summary.isEmpty()) {
            seoul.setText(c.getString(R.string.alt_seoul, alight, main.summary));
        } else {
            seoul.setText(c.getString(R.string.alt_seoul_stop_only, alight));
        }

        // 건너뜀이 지연보다 우선
        TextView note = v.findViewById(R.id.alt_option_note);
        if (o.skipped) {
            note.setText(R.string.alt_skipped);
            note.setTextColor(color(c, R.color.alt_low));
            note.setVisibility(View.VISIBLE);
            v.setAlpha(0.6f);
        } else if (o.delayMin > 0) {
            note.setText(c.getString(R.string.alt_delay, o.delayMin));
            note.setTextColor(color(c, R.color.alt_mid));
            note.setVisibility(View.VISIBLE);
        }

        bindActual(v, o);
        bindBuses(inflater, v, o);
        return v;
    }

    /** 지난 출근길 재생 — 그날 실제로 (A 홈 카드와 같은 문구 · 색) */
    private static void bindActual(View card, Option o) {
        Context c = card.getContext();
        TextView actual = card.findViewById(R.id.alt_option_actual);
        switch (AltFormat.actualKind(o)) {
            case BOARDED: {
                String boarded = (o.actual.fullSkipped > 0 ? c.getString(R.string.alt_actual_skipped, o.actual.fullSkipped) : "")
                        + o.actual.boardedAt;
                String total = AltFormat.duration(o.actual.totalMin);
                actual.setText(o.actual.arriveAt == null ? c.getString(R.string.alt_actual_boarded_only, boarded)
                        : total == null ? c.getString(R.string.alt_actual_boarded, boarded, o.actual.arriveAt)
                        : c.getString(R.string.alt_actual_boarded_total, boarded, o.actual.arriveAt, total));
                actual.setTextColor(color(c, R.color.alt_primary));
                actual.setVisibility(View.VISIBLE);
                break;
            }
            case NOT_BOARDED:
                actual.setText(R.string.alt_actual_none);
                actual.setTextColor(color(c, R.color.alt_low));
                actual.setVisibility(View.VISIBLE);
                break;
            default:
                actual.setVisibility(View.GONE);
        }
    }

    /** 3줄: 어떻게 타는지 */
    private static String plan(Context c, Option o) {
        String stop = o.stop == null || o.stop.name == null ? "" : o.stop.name;
        if (Option.KIND_WAIT.equals(o.kind)) {
            Bus next = AltFormat.nextBetterBus(o);
            if (next != null) {
                return c.getString(R.string.alt_plan_wait_next, o.route, eta(c, next), seats(c, next));
            }
            return c.getString(R.string.alt_plan_wait, stop);
        }
        if (Option.KIND_WALK.equals(o.kind)) return c.getString(R.string.alt_plan_walk, stop, o.walkMin);
        return o.walkMin <= 0 ? c.getString(R.string.alt_plan_other_here, stop)
                : c.getString(R.string.alt_plan_other, stop, o.walkMin);
    }

    /** 다가오는 차량별 좌석 (11주차 2차) */
    private static void bindBuses(LayoutInflater inflater, View card, Option o) {
        Context c = card.getContext();
        LinearLayout list = card.findViewById(R.id.alt_option_buses);
        List<AltFormat.BusRow> rows = AltFormat.busRows(o);
        card.findViewById(R.id.alt_option_buses_none).setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        for (AltFormat.BusRow row : rows) {
            View r = inflater.inflate(R.layout.alt_item_bus, list, false);
            ((TextView) r.findViewById(R.id.alt_bus_order)).setText(c.getString(R.string.alt_bus_order, row.bus.order));
            ((TextView) r.findViewById(R.id.alt_bus_eta)).setText(eta(c, row.bus));
            TextView seats = r.findViewById(R.id.alt_bus_seats);
            seats.setText(seats(c, row.bus));
            r.findViewById(R.id.alt_bus_first).setVisibility(row.firstReachable && !o.skipped ? View.VISIBLE : View.GONE);
            TextView prob = r.findViewById(R.id.alt_bus_prob);
            if (row.missed) {
                prob.setText(R.string.alt_bus_missed);
                prob.setTextColor(color(c, R.color.alt_muted));
                prob.setTypeface(null, android.graphics.Typeface.NORMAL);
                seats.setTextColor(color(c, R.color.alt_muted));
                r.setAlpha(0.55f);
            } else if (!o.showsProbability()) {
                prob.setText(o.isStandingOk() ? R.string.alt_standing : R.string.alt_seat_unknown);
                prob.setTextColor(color(c, o.isStandingOk() ? R.color.alt_high : R.color.alt_muted));
                seats.setTextColor(color(c, R.color.alt_muted));
            } else {
                prob.setText(AltFormat.percent(row.bus.boardProb));
                prob.setTextColor(color(c, AltStyle.color(row.level)));
                seats.setTextColor(color(c, row.seats == AltFormat.Seats.UNKNOWN
                        ? R.color.alt_muted : AltStyle.color(row.level)));
            }
            list.addView(r);
        }
    }

    static String eta(Context c, Bus b) {
        return b.etaMin <= 0 ? c.getString(R.string.alt_bus_now) : c.getString(R.string.alt_bus_eta, b.etaMin);
    }

    static String seats(Context c, Bus b) {
        switch (AltFormat.seats(b)) {
            case FULL:
                return c.getString(R.string.alt_seats_full);
            case SOME:
                return c.getString(R.string.alt_seats_some, b.seatsAtStop);
            default:
                return c.getString(R.string.alt_seats_unknown);
        }
    }

    private static int color(Context c, int res) {
        return ContextCompat.getColor(c, res);
    }
}
