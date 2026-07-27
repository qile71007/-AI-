
package com.fongmi.android.tv.ui.dialog;

import androidx.fragment.app.FragmentActivity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;

public class LinkDialog extends DialogFragment {
    public static void show(FragmentActivity activity, OnLinkListener listener) { new LinkDialog().show(activity.getSupportFragmentManager(), "link"); }
    @NonNull @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        EditText input = new EditText(requireActivity()); input.setHint("请输入链接地址"); input.setPadding(24, 16, 24, 16);
        return new AlertDialog.Builder(requireActivity()).setTitle("链接").setView(input).setPositiveButton(R.string.dialog_positive, null).setNegativeButton(R.string.dialog_negative, null).create();
    }
    public interface OnLinkListener { void onLink(String url); }
}
