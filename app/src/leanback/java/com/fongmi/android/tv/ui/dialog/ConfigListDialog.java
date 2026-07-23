package com.fongmi.android.tv.ui.dialog;

import android.os.Parcelable;
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
import com.fongmi.android.tv.utils.SecretConfigManager;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ConfigListDialog extends BaseAlertDialog implements ConfigAdapter.OnClickListener {

    private static Parcelable savedState;

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
    public void show(androidx.fragment.app.FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
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
        Config current = getCurrentConfig();
        adapter = new ConfigAdapter(this);
        adapter.addAll(type, current);
        adapter.setShowSecret(false);
        adapter.setUnlockedKeyword(null);
        binding.recycler.setAdapter(adapter);
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);

        if (current != null) {
            String currentUrl = current.getUrl();
            binding.recycler.post(() -> {
                for (int i = 0; i < adapter.getItemCount(); i++) {
                    Config item = adapter.getItem(i);
                    if (item != null && currentUrl != null && currentUrl.equals(item.getUrl())) {
                        LinearLayoutManager lm = (LinearLayoutManager) binding.recycler.getLayoutManager();
                        if (lm != null) lm.scrollToPositionWithOffset(i, 0);
                        break;
                    }
                }
            });
        }

        if (savedState != null) {
            binding.recycler.getLayoutManager().onRestoreInstanceState(savedState);
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
                String keyword = s == null ? "" : s.toString().trim();

                // ========== Global lock (highest priority) ==========
                if (keyword.equals("上锁")) {
                    SecretConfigManager.getInstance().lock();
                    adapter.setShowSecret(false);
                    adapter.setUnlockedKeyword(null);
                    binding.keyword.setText("");
                    return;
                }

                // ========== Per-item unlock by keyword ==========
                if (adapter.unlockByKeyword(keyword)) {
                    binding.keyword.setText("");
                    adapter.filter("");
                    return;
                }

                // ========== Global unlock: dynamic match ==========
                if (SecretConfigManager.getInstance().unlock(keyword)) {
                    adapter.setShowSecret(true);
                    adapter.setUnlockedKeyword(null);
                    binding.keyword.setText("");
                    return;
                }

                // ========== Keyword filter ==========
                if (adapter != null) adapter.filter(keyword);
                binding.recycler.scrollToPosition(0);
                savedState = null;
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (binding != null && binding.recycler.getLayoutManager() != null) {
            savedState = binding.recycler.getLayoutManager().onSaveInstanceState();
        }
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
        if (adapter.getAllItemCount() == 0) dismiss();
    }
}
