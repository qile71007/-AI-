package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.ActivityHistoryBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import java.util.List;

public class HistoryActivity extends BaseActivity {
    private ActivityHistoryBinding mBinding;
    public static void start(Activity activity) { activity.startActivity(new Intent(activity, HistoryActivity.class)); }
    @Override protected ViewBinding getBinding() { return mBinding = ActivityHistoryBinding.inflate(getLayoutInflater()); }
    @Override protected void initView(Bundle savedInstanceState) {
        mBinding.recycler.setLayoutManager(new LinearLayoutManager(this));
        List<History> list = History.get();
        mBinding.recycler.setAdapter(new RecyclerView.Adapter<HistoryViewHolder>() {
            @Override public HistoryViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                TextView tv = new TextView(parent.getContext());
                tv.setPadding(24, 16, 24, 16); tv.setTextSize(18);
                return new HistoryViewHolder(tv);
            }
            @Override public void onBindViewHolder(HistoryViewHolder holder, int position) {
                History h = list.get(position);
                holder.textView.setText(h.getVodName());
                holder.textView.setOnClickListener(v -> VideoActivity.start(HistoryActivity.this, h.getSiteKey(), h.getVodId(), h.getVodName(), h.getVodPic()));
            }
            @Override public int getItemCount() { return list.size(); }
        });
    }
    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        HistoryViewHolder(TextView tv) { super(tv); textView = tv; }
    }
}
