
package com.fongmi.android.tv.ui.dialog;

import androidx.fragment.app.FragmentActivity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import java.util.ArrayList;
import java.util.List;

public class EpisodeGridDialog extends DialogFragment {
    private final List<Episode> mEpisodes = new ArrayList<>();
    public static void show(FragmentActivity activity, List<Episode> episodes) { EpisodeGridDialog d = new EpisodeGridDialog(); if (episodes != null) d.mEpisodes.addAll(episodes); d.show(activity.getSupportFragmentManager(), "episode_grid"); }
    @NonNull @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        RecyclerView recycler = new RecyclerView(requireActivity()); recycler.setLayoutManager(new GridLayoutManager(requireActivity(), 5)); recycler.setPadding(24, 16, 24, 16);
        recycler.setAdapter(new RecyclerView.Adapter<EpisodeViewHolder>() {
            @NonNull @Override public EpisodeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) { TextView tv = new TextView(requireActivity()); tv.setPadding(16, 12, 16, 12); tv.setTextSize(14); tv.setGravity(android.view.Gravity.CENTER); return new EpisodeViewHolder(tv); }
            @Override public void onBindViewHolder(@NonNull EpisodeViewHolder holder, int position) { Episode ep = mEpisodes.get(position); holder.textView.setText(ep.getName()); holder.textView.setOnClickListener(v -> dismiss()); }
            @Override public int getItemCount() { return mEpisodes.size(); }
        });
        return new AlertDialog.Builder(requireActivity()).setTitle("选集").setView(recycler).setNegativeButton(R.string.dialog_negative, null).create();
    }
    static class EpisodeViewHolder extends RecyclerView.ViewHolder { TextView textView; EpisodeViewHolder(TextView tv) { super(tv); textView = tv; } }
}
