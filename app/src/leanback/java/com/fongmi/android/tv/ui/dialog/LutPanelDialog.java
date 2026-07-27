
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
import com.fongmi.android.tv.player.lut.LutPreset;
import com.fongmi.android.tv.player.lut.LutSetting;
import com.fongmi.android.tv.player.lut.LutStore;
import java.util.ArrayList;
import java.util.List;

public class LutPanelDialog extends DialogFragment {
    private final List<LutPreset> mPresets = new ArrayList<>();
    private PresetAdapter mAdapter;
    public static void show(FragmentFragmentActivity activity) { new LutPanelDialog().show(activity.getSupportFragmentManager(), "lut_panel"); }
    @Override public void onStart() { super.onStart(); loadPresets(); }
    @NonNull @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        RecyclerView recycler = new RecyclerView(requireActivity());
        recycler.setLayoutManager(new LinearLayoutManager(requireActivity()));
        recycler.setPadding(24, 16, 24, 16);
        mAdapter = new PresetAdapter(); recycler.setAdapter(mAdapter);
        return new AlertDialog.Builder(requireActivity())
                .setTitle(getString(R.string.player_lut) + " - " + LutSetting.getSummary())
                .setView(recycler)
                .setNeutralButton(getString(R.string.lut_reset), (dialog, which) -> { LutSetting.select(null); dismiss(); })
                .setNegativeButton(R.string.dialog_negative, null)
                .create();
    }
    private void loadPresets() { mPresets.clear(); mPresets.addAll(LutStore.getCachedPresets()); if (mAdapter != null) mAdapter.notifyDataSetChanged(); }
    private class PresetAdapter extends RecyclerView.Adapter<PresetAdapter.ViewHolder> {
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) { TextView tv = new TextView(requireActivity()); tv.setPadding(24, 16, 24, 16); tv.setTextSize(16); tv.setSingleLine(true); return new ViewHolder(tv); }
        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) { LutPreset preset = mPresets.get(position); holder.textView.setText(preset.getName()); holder.textView.setOnClickListener(v -> { LutSetting.select(preset); dismiss(); }); }
        @Override public int getItemCount() { return mPresets.size(); }
        class ViewHolder extends RecyclerView.ViewHolder { TextView textView; ViewHolder(TextView tv) { super(tv); textView = tv; } }
    }
}
