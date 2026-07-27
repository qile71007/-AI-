package com.fongmi.android.tv.ui.dialog;

import androidx.fragment.app.FragmentActivity;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;

public class FilterDialog extends DialogFragment {
    public static void show(FragmentFragmentActivity activity) {
        new FilterDialog().show(activity.getSupportFragmentManager(), "filter");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.setting_filter)
                .setMessage("过滤功能")
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
    }
}
