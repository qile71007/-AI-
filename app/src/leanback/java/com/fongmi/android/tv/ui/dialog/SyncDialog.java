package com.fongmi.android.tv.ui.dialog;

import androidx.fragment.app.FragmentActivity;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.HistorySyncUtil;

public class SyncDialog extends DialogFragment {
    public static void show(FragmentActivity activity) {
        new SyncDialog().show(activity.getSupportFragmentManager(), "sync");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String[] items = {getString(R.string.sync_upload), getString(R.string.sync_download)};
        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.setting_sync)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) HistorySyncUtil.upload(requireContext(), null);
                    else HistorySyncUtil.download(requireContext(), null);
                }).create();
    }
}
