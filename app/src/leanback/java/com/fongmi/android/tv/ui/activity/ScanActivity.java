package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.viewbinding.ViewBinding;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityScanBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;

public class ScanActivity extends BaseActivity {
    private ActivityScanBinding mBinding;
    public static void start(Activity activity) { activity.startActivity(new Intent(activity, ScanActivity.class)); }
    @Override protected ViewBinding getBinding() { return mBinding = ActivityScanBinding.inflate(getLayoutInflater()); }
    @Override protected void initView(Bundle savedInstanceState) { }
}
