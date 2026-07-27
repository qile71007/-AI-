package com.fongmi.android.tv.ui.adapter;

import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class EpisodeGroupAdapter extends RecyclerView.Adapter<EpisodeGroupAdapter.ViewHolder> {
    private final List<String> items = new ArrayList<>();
    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView tv = new TextView(parent.getContext());
        tv.setPadding(16, 12, 16, 12);
        tv.setTextSize(14);
        return new ViewHolder(tv);
    }
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.textView.setText(items.get(position));
    }
    @Override public int getItemCount() { return items.size(); }
    public void addAll(List<String> list) { items.clear(); items.addAll(list); notifyDataSetChanged(); }
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ViewHolder(TextView tv) { super(tv); textView = tv; }
    }
}
