package kr.ac.kookmin.seatforecast.home.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import kr.ac.kookmin.seatforecast.home.R;
import kr.ac.kookmin.seatforecast.home.model.Option;

/** 추천 목록 (ranking 순서 그대로). 10주차: 노선 · 정류장 이름만 보이는 임시 항목. */
class OptionAdapter extends RecyclerView.Adapter<OptionAdapter.Holder> {

    interface OnOptionClick {
        void onClick(Option option);
    }

    private final List<Option> items = new ArrayList<>();
    private final OnOptionClick onClick;

    OptionAdapter(OnOptionClick onClick) {
        this.onClick = onClick;
    }

    void submit(List<Option> options) {
        items.clear();
        items.addAll(options);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.home_item_option, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Option o = items.get(position);
        h.route.setText(o.route);
        h.stop.setText(o.stop == null ? "" : o.stop.name);   // API 이름 그대로
        h.itemView.setOnClickListener(v -> onClick.onClick(o));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView route;
        final TextView stop;

        Holder(View v) {
            super(v);
            route = v.findViewById(R.id.home_option_route);
            stop = v.findViewById(R.id.home_option_stop);
        }
    }
}
