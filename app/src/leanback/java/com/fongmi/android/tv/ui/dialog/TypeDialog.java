package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;

public class TypeDialog extends DialogFragment {
    public static void show(Activity activity) {
        new TypeDialog().show(activity.getFragmentManager(), "type");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireActivity())
                .setTitle("选择分类")
                .setItems(new String[]{"全部", "电影", "电视剧", "综艺", "动漫"}, (dialog, which) -> {})
                .create();
    }
}
