
package com.fongmi.android.tv.ui.dialog;

import androidx.fragment.app.FragmentActivity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.EpgData;
import java.util.ArrayList;
import java.util.List;

public class LiveEpgDialog extends DialogFragment {
    private final List<EpgData> mEpgData = new ArrayList<>();
    public static void show(FragmentActivity activity, List<EpgData> epgData) { LiveEpgDialog d = new LiveEpgDialog(); if (epgData != null) d.mEpgData.addAll(epgData); d.show(activity.getSupportFragmentManager(), "live_epg"); }
    @NonNull @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        RecyclerView recycler = new RecyclerView(requireActivity()); recycler.setLayoutManager(new LinearLayoutManager(requireActivity())); recycler.setPadding(24, 16, 24, 16);
        recycler.setAdapter(new RecyclerView.Adapter<EpgViewHolder>() {
            @NonNull @Override public EpgViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) { TextView tv = new TextView(requireActivity()); tv.setPadding(24, 12, 24, 12); tv.setTextSize(14); return new EpgViewHolder(tv); }
            @Override public void onBindViewHolder(@NonNull EpgViewHolder holder, int position) { EpgData data = mEpgData.get(position); holder.textView.setText(data.getTitle() + " " + data.getStartTime()); }
            @Override public int getItemCount() { return mEpgData.size(); }
        });
        return new AlertDialog.Builder(requireActivity()).setTitle("节目单").setView(recycler).setNegativeButton(R.string.dialog_negative, null).create();
    }
    static class EpgViewHolder extends RecyclerView.ViewHolder { TextView textView; EpgViewHolder(TextView tv) { super(tv); textView = tv; } }
}
