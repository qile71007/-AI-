package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;

public class FilterDialog extends DialogFragment {
    public static void show(Activity activity) {
        new FilterDialog().show(activity.getFragmentManager(), "filter");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.setting_filter)
                .setMessage(R.string.setting_filter_summary)
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
    }
}
