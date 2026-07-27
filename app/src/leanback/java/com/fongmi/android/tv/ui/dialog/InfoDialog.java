package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;

public class InfoDialog extends DialogFragment {
    private String title;
    private String message;
    public static void show(Activity activity, String title, String message) {
        InfoDialog dialog = new InfoDialog();
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putString("message", message);
        dialog.setArguments(args);
        dialog.show(activity.getFragmentManager(), "info");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        String t = args != null ? args.getString("title", "") : "";
        String m = args != null ? args.getString("message", "") : "";
        return new AlertDialog.Builder(requireActivity())
                .setTitle(t)
                .setMessage(m)
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
    }
}
