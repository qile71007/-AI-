
package com.fongmi.android.tv.ui.dialog;

import androidx.fragment.app.FragmentActivity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;

public class VideoContentDialog extends DialogFragment {
    public static void show(FragmentActivity activity, String content) { VideoContentDialog d = new VideoContentDialog(); Bundle args = new Bundle(); args.putString("content", content); d.setArguments(args); d.show(activity.getSupportFragmentManager(), "video_content"); }
    @NonNull @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        String content = getArguments() != null ? getArguments().getString("content", "") : "";
        TextView textView = new TextView(requireActivity()); textView.setText(content); textView.setPadding(24, 16, 24, 16); textView.setTextSize(14);
        ScrollView scrollView = new ScrollView(requireActivity()); scrollView.addView(textView);
        return new AlertDialog.Builder(requireActivity()).setTitle("内容").setView(scrollView).setPositiveButton(R.string.dialog_positive, null).create();
    }
}
