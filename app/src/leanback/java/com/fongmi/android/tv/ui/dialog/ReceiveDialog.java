package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;

public class ReceiveDialog extends DialogFragment {
    public static void show(Activity activity) {
        new ReceiveDialog().show(activity.getFragmentManager(), "receive");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.player_receive)
                .setMessage("正在接收投屏...")
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
    }
}
