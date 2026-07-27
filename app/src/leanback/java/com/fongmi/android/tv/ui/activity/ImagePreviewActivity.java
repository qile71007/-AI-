package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.viewbinding.ViewBinding;
import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityImagePreviewBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;

public class ImagePreviewActivity extends BaseActivity {
    private ActivityImagePreviewBinding mBinding;
    public static void start(Activity activity, String url) {
        Intent intent = new Intent(activity, ImagePreviewActivity.class);
        intent.putExtra("url", url);
        activity.startActivity(intent);
    }
    @Override protected ViewBinding getBinding() { return mBinding = ActivityImagePreviewBinding.inflate(getLayoutInflater()); }
    @Override protected void initView(Bundle savedInstanceState) {
        String url = getIntent().getStringExtra("url");
        Glide.with(this).load(url).into(mBinding.image);
    }
}
