package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;

public class VideoContentDialog extends DialogFragment {
    public static void show(Activity activity, String content) {
        VideoContentDialog dialog = new VideoContentDialog();
        Bundle args = new Bundle();
        args.putString("content", content);
        dialog.setArguments(args);
        dialog.show(activity.getFragmentManager(), "video_content");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String content = getArguments() != null ? getArguments().getString("content", "") : "";
        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.player_content)
                .setMessage(content)
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
    }
}
