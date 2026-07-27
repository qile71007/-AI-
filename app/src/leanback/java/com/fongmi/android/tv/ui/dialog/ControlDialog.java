package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;

public class ControlDialog extends DialogFragment {
    public static void show(Activity activity) {
        new ControlDialog().show(activity.getFragmentManager(), "control");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.player_control)
                .setItems(new String[]{"播放/暂停", "停止", "重试"}, (dialog, which) -> {})
                .create();
    }
}
