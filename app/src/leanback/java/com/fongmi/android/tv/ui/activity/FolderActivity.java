package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityFolderBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.fragment.FolderFragment;
import java.util.ArrayList;
import java.util.List;

public class FolderActivity extends BaseActivity {
    private ActivityFolderBinding mBinding;
    private String mKey;
    private Class mType;
    private final List<Vod> mItems = new ArrayList<>();

    public static void start(Activity activity, String key, Class type) {
        Intent intent = new Intent(activity, FolderActivity.class);
        intent.putExtra("key", key);
        intent.putExtra("type", type);
        activity.startActivity(intent);
    }

    @Override protected ViewBinding getBinding() { return mBinding = ActivityFolderBinding.inflate(getLayoutInflater()); }
    @Override protected void initView(Bundle savedInstanceState) {
        mKey = getIntent().getStringExtra("key");
        mType = getIntent().getParcelableExtra("type");
        mBinding.recycler.setLayoutManager(new LinearLayoutManager(this));
    }
}
