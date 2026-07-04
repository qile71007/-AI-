package com.fongmi.android.tv.ui.dialog;

import android.text.Editable;
import android.view.inputmethod.EditorInfo;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.DialogConfigListBinding;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.ui.adapter.ConfigAdapter;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ConfigListDialog extends BaseAlertDialog implements ConfigAdapter.OnClickListener {

    private DialogConfigListBinding binding;
    private ConfigListener listener;
    private ConfigAdapter adapter;
    private int type;

    public static ConfigListDialog create() {
        return new ConfigListDialog();
    }

    public ConfigListDialog type(int type) {
        this.type = type;
        return this;
    }

    public ConfigListDialog listener(ConfigListener listener) {
        this.listener = listener;
        return this;
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogConfigListBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        String title;
        switch (type) {
            case 0: title = getString(R.string.setting_vod); break;
            case 1: title = getString(R.string.setting_live); break;
            case 2: title = getString(R.string.setting_wall); break;
            default: title = "配置";
        }
        return builder().setTitle(title).setView(getBinding().getRoot())
                .setNegativeButton(R.string.dialog_negative, null);
    }

    @Override
    protected void initView() {
        adapter = new ConfigAdapter(this);
        adapter.addAll(type);
        binding.recycler.setAdapter(adapter);
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);

        // 滚动到当前选中的配置
        Config current = getCurrentConfig();
        if (current != null) {
            int position = adapter.findPosition(current);
            if (position != -1) {
                adapter.setSelectedPosition(position);
                binding.recycler.post(() -> binding.recycler.smoothScrollToPosition(position));
            }
        }
    }

    @Override
    protected void initEvent() {
        binding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) Util.hideKeyboard(binding.keyword);
            return false;
        });
        binding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable s) {
                adapter.filter(s.toString());
                // 过滤后重新定位到当前配置
                Config current = getCurrentConfig();
                if (current != null) {
                    int position = adapter.findPosition(current);
                    if (position != -1) {
                        adapter.setSelectedPosition(position);
                        binding.recycler.post(() -> binding.recycler.smoothScrollToPosition(position));
                    }
                }
                binding.recycler.scrollToPosition(0);
            }
        });
    }

    private Config getCurrentConfig() {
        switch (type) {
            case 0: return VodConfig.get().getConfig();
            case 1: return LiveConfig.get().getConfig();
            case 2: return WallConfig.get().getConfig();
            default: return null;
        }
    }

    @Override
    public void onTextClick(Config item) {
        if (listener != null) listener.setConfig(item);
        dismiss();
    }

    @Override
    public void onDeleteClick(Config item) {
        int count = adapter.remove(item);
        if (count == 0) dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() == 0) dismiss();
    }
}