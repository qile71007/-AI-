package com.fongmi.android.tv.ui.adapter;

import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fongmi.android.tv.bean.History;
import java.util.ArrayList;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
    private final List<History> items = new ArrayList<>();
    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView tv = new TextView(parent.getContext());
        tv.setPadding(24, 16, 24, 16);
        tv.setTextSize(16);
        return new ViewHolder(tv);
    }
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.textView.setText(items.get(position).getVodName());
    }
    @Override public int getItemCount() { return items.size(); }
    public void addAll(List<History> list) { items.clear(); items.addAll(list); notifyDataSetChanged(); }
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ViewHolder(TextView tv) { super(tv); textView = tv; }
    }
}
