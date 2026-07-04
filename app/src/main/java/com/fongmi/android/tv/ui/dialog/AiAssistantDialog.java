package com.fongmi.android.tv.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogAiAssistantBinding;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AiAssistantDialog extends DialogFragment {

    private static final String TAG = "AiAssistantDialog";

    // ==================== 模型配置 ====================
    private static final String DOUBAO_API_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";
    private static final String DOUBAO_MODEL = "doubao-seed-1-6-251015";
    private static final String QWEN_API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String QWEN_MODEL = "qwen-plus";
    private static final String GLM_API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    private static final String GLM_MODEL = "glm-4-flash";
    private static final String DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final String DEEPSEEK_MODEL = "deepseek-chat";

    // ==================== Key存储键名 ====================
    private static final String PREF_NAME = "ai_config";
    private static final String KEY_DOUBAO = "doubao_api_key";
    private static final String KEY_QWEN = "qwen_api_key";
    private static final String KEY_GLM = "glm_api_key";
    private static final String KEY_DEEPSEEK = "deepseek_api_key";
    private static final String KEY_MODEL_INDEX = "model_index";
    private static final String KEY_MESSAGES = "chat_messages";

    // ==================== 模型索引 ====================
    private static final int MODEL_DOUBAO = 0;
    private static final int MODEL_QWEN = 1;
    private static final int MODEL_GLM = 2;
    private static final int MODEL_DEEPSEEK = 3;

    // ==================== 远程配置URL ====================
    private static final String REMOTE_CONFIG_URL = "https://gh.llkk.cc/https://raw.githubusercontent.com/jllyzjs/qile/main/qile.json";

    // ==================== 内置备用配置（20个稳定源） ====================
    private static final List<ConfigItem> FALLBACK_CONFIGS = Arrays.asList(
            new ConfigItem("饭太硬", "http://www.饭太硬.com/tv"),
            new ConfigItem("肥猫", "http://肥猫.com"),
            new ConfigItem("高天流云0821", "https://gh.llkk.cc/https://github.com/gaotianliuyun/gao/raw/master/0821.json"),
            new ConfigItem("高天流云0825", "https://gh.llkk.cc/https://github.com/gaotianliuyun/gao/raw/master/0825.json"),
            new ConfigItem("高天流云JS", "https://gh.llkk.cc/https://github.com/gaotianliuyun/gao/raw/master/js.json"),
            new ConfigItem("刘673", "https://fastly.jsdelivr.net/gh/liu673cn/box@main/m.json"),
            new ConfigItem("猫点播", "https://tvbox.catvod.com/catvod.json"),
            new ConfigItem("七乐", "https://cnb.cool/qile71007/qile/-/git/raw/main/7l.json"),
            new ConfigItem("晴天", "http://43.138.212.149:8866/api.json"),
            new ConfigItem("cc0cd", "https://tv.cc0cd.cc.cd"),
            new ConfigItem("qist", "https://gh-proxy.com/https://raw.githubusercontent.com/qist/tvbox/master/jsm.json"),
            new ConfigItem("游魂", "https://www.iyouhun.com/tv/wex"),
            new ConfigItem("春秋导航", "http://yydsok.com/api/bendi/yydsapi/nf/api.json"),
            new ConfigItem("乐哥追剧", "https://xn--fjq53n.xyz/TV/zy.json"),
            new ConfigItem("瓜子", "http://api.fumilong.com/%E7%93%9C%E5%AD%90.json"),
            new ConfigItem("青檬影视", "https://11293.kstore.space/%E8%A5%BF%E6%9F%9Ays.json"),
            new ConfigItem("日后abc", "http://rihou.cc:88/demoabc.json"),
            new ConfigItem("EasyXC", "https://gh-proxy.com/https://raw.githubusercontent.com/EasyXC/EasyTVbox/main/easytv.jpg"),
            new ConfigItem("无意云", "https://ym.wya6.cn"),
            new ConfigItem("蜗牛", "https://tv.xn--11x555b.top/svip")
    );

    // ==================== 推荐相关 ====================
    private List<ConfigItem> allConfigs = new ArrayList<>();
    private List<ConfigItem> remainingConfigs = new ArrayList<>();
    private List<ConfigItem> lastRecommended = null;
    private boolean isLoadingRemote = false;

    // ==================== UI组件 ====================
    private DialogAiAssistantBinding binding;
    private MessageAdapter adapter;
    private List<Message> messages = new ArrayList<>();
    private OkHttpClient client;
    private int configType;
    private String detectedConfigUrl;
    private String detectedConfigName;
    private String currentApiKey;
    private int currentModel = MODEL_DOUBAO;
    private Gson gson = new Gson();

    // ==================== ConfigItem ====================
    static class ConfigItem {
        String name;
        String url;
        ConfigItem(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_App);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogAiAssistantBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 对话框背景为白色（不透明）
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        }

        if (getArguments() != null) {
            configType = getArguments().getInt("config_type", 0);
        }

        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        loadMessages();

        adapter = new MessageAdapter(messages);
        binding.rvMessages.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvMessages.setAdapter(adapter);

        binding.toolbar.setNavigationOnClickListener(v -> dismiss());

        setupModelSelector();

        binding.tvGetKey.setOnClickListener(v -> {
            String url = getKeyApplyUrl(currentModel);
            Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browser);
        });

        binding.btnSaveKey.setOnClickListener(v -> saveApiKey());
        binding.btnSend.setOnClickListener(v -> sendMessage());
        binding.btnAddConfig.setOnClickListener(v -> addConfigToDialog());

        // ---------- 修改Key文字点击 ----------
        binding.btnModifyKey.setOnClickListener(v -> {
            if (binding.keySettingCard.getVisibility() == View.GONE) {
                loadApiKey();
                binding.etApiKey.setText(currentApiKey);
                showKeySettingCard("重新配置 " + getModelName(currentModel) + " 的 API Key");
            } else {
                binding.etApiKey.requestFocus();
            }
        });

        loadApiKeyAndUpdateUI();

        if (messages.isEmpty()) {
            String welcome = "👋 当前使用 " + getModelName(currentModel) + "\n\n"
                    + "📌 **功能**：\n"
                    + "  • 智能搜索TVBox配置\n"
                    + "  • 随机推荐精选配置\n\n"
                    + "💡 **使用方法**：\n"
                    + "  • 输入 **\"推荐配置\"** 获取5个配置，回复数字1-5即可加载\n"
                    + "  • 直接描述需求，如\"推荐一个稳定的点播配置\"\n"
                    + "  • 粘贴配置链接，AI自动识别并添加\n\n";
            addSystemMessage(welcome);
        } else {
            binding.rvMessages.scrollToPosition(messages.size() - 1);
        }
    }

    // ==================== 模型选择相关 ====================
    private void setupModelSelector() {
        binding.tvCurrentModel.setText(getModelName(currentModel));
        binding.modelSelector.setOnClickListener(v -> showModelSelectDialog());
    }

    private void showModelSelectDialog() {
        String[] modelNames = getResources().getStringArray(R.array.model_names);
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), android.R.style.Theme_Material_Dialog_Alert);
        builder.setTitle("选择模型");
        builder.setSingleChoiceItems(modelNames, currentModel, (dialog, which) -> {
            if (which != currentModel) {
                currentModel = which;
                requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .edit().putInt(KEY_MODEL_INDEX, currentModel).apply();
                binding.tvCurrentModel.setText(getModelName(currentModel));
                resetDetectedConfig();
                loadApiKeyAndUpdateUI();
                addSystemMessage("🔁 已切换到 " + getModelName(currentModel));
            }
            dialog.dismiss();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // ==================== 数据持久化 ====================
    private void loadMessages() {
        String json = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_MESSAGES, "");
        if (!TextUtils.isEmpty(json)) {
            Type type = new TypeToken<List<Message>>(){}.getType();
            List<Message> saved = gson.fromJson(json, type);
            if (saved != null) {
                messages.clear();
                messages.addAll(saved);
            }
        }
        int savedIndex = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_MODEL_INDEX, MODEL_DOUBAO);
        currentModel = savedIndex;
    }

    private void saveMessages() {
        String json = gson.toJson(messages);
        requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_MESSAGES, json).apply();
    }

    // ==================== API Key管理 ====================
    private void loadApiKeyAndUpdateUI() {
        loadApiKey();
        if (TextUtils.isEmpty(currentApiKey)) {
            showKeySettingCard("请先配置 " + getModelName(currentModel) + " 的 API Key");
            binding.btnSend.setEnabled(false);
        } else {
            hideKeySettingCard();
            binding.btnSend.setEnabled(true);
            binding.etInput.setHint("输入你想搜索的配置...");
            resetDetectedConfig();
        }
        binding.tvGetKey.setText("📌 免费申请 " + getModelName(currentModel) + " API Key");
    }

    private void loadApiKey() {
        String key;
        switch (currentModel) {
            case MODEL_DOUBAO:
                key = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .getString(KEY_DOUBAO, "");
                break;
            case MODEL_QWEN:
                key = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .getString(KEY_QWEN, "");
                break;
            case MODEL_GLM:
                key = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .getString(KEY_GLM, "");
                break;
            case MODEL_DEEPSEEK:
                key = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .getString(KEY_DEEPSEEK, "");
                break;
            default:
                key = "";
        }
        currentApiKey = key;
    }

    private void saveApiKey() {
        String key = binding.etApiKey.getText().toString().trim();
        if (TextUtils.isEmpty(key)) {
            binding.tvKeyStatus.setText("❌ Key 不能为空");
            binding.tvKeyStatus.setVisibility(View.VISIBLE);
            return;
        }
        if (currentModel == MODEL_DEEPSEEK && !key.startsWith("sk-")) {
            binding.tvKeyStatus.setText("⚠️ DeepSeek Key 应以 sk- 开头");
            binding.tvKeyStatus.setVisibility(View.VISIBLE);
            return;
        }
        String prefKey;
        switch (currentModel) {
            case MODEL_DOUBAO: prefKey = KEY_DOUBAO; break;
            case MODEL_QWEN: prefKey = KEY_QWEN; break;
            case MODEL_GLM: prefKey = KEY_GLM; break;
            case MODEL_DEEPSEEK: prefKey = KEY_DEEPSEEK; break;
            default: prefKey = KEY_DOUBAO;
        }
        requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(prefKey, key).apply();
        currentApiKey = key;
        binding.tvKeyStatus.setText("✅ " + getModelName(currentModel) + " Key 保存成功！");
        binding.tvKeyStatus.setVisibility(View.VISIBLE);
        binding.btnSend.setEnabled(true);
        binding.etInput.setHint("输入你想搜索的配置...");
        resetDetectedConfig();
        binding.keySettingCard.postDelayed(() -> {
            hideKeySettingCard();
            addSystemMessage("🔑 " + getModelName(currentModel) + " Key 已配置，可以开始对话了！");
        }, 1000);
    }

    // ==================== 模型信息方法 ====================
    private String getApiUrl() {
        switch (currentModel) {
            case MODEL_DOUBAO: return DOUBAO_API_URL;
            case MODEL_QWEN: return QWEN_API_URL;
            case MODEL_GLM: return GLM_API_URL;
            case MODEL_DEEPSEEK: return DEEPSEEK_API_URL;
            default: return DOUBAO_API_URL;
        }
    }

    private String getModelId() {
        switch (currentModel) {
            case MODEL_DOUBAO: return DOUBAO_MODEL;
            case MODEL_QWEN: return QWEN_MODEL;
            case MODEL_GLM: return GLM_MODEL;
            case MODEL_DEEPSEEK: return DEEPSEEK_MODEL;
            default: return DOUBAO_MODEL;
        }
    }

    private String getModelName(int model) {
        switch (model) {
            case MODEL_DOUBAO:
                return "豆包火山";
            case MODEL_QWEN:
                return "通义千问";
            case MODEL_GLM:
                return "智谱清言";
            case MODEL_DEEPSEEK:
                return "DeepSeek";
            default:
                return "未知";
        }
    }

    private String getKeyApplyUrl(int model) {
        switch (model) {
            case MODEL_DOUBAO: return "https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey";
            case MODEL_QWEN: return "https://dashscope.console.aliyun.com/apiKey";
            case MODEL_GLM: return "https://open.bigmodel.cn/usercenter/apikeys";
            case MODEL_DEEPSEEK: return "https://platform.deepseek.com/api_keys";
            default: return "https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey";
        }
    }

    // ==================== 核心：发送消息 ====================
    private void sendMessage() {
        String input = binding.etInput.getText().toString().trim();
        if (TextUtils.isEmpty(input)) return;

        // ---------- 处理数字选择（1-5） ----------
        if (lastRecommended != null && input.matches("[1-5]")) {
            int index = Integer.parseInt(input) - 1;
            if (index >= 0 && index < lastRecommended.size()) {
                ConfigItem selected = lastRecommended.get(index);
                detectedConfigUrl = selected.url;
                detectedConfigName = selected.name;
                addUserMessage(input);
                addAiMessage("✅ 已选择 **" + selected.name + "**\n📎 " + selected.url);
                binding.btnAddConfig.setEnabled(true);
                binding.btnAddConfig.setText("✅ 添加此配置：" + selected.name);
                lastRecommended = null;
                binding.etInput.setText("");
                return;
            }
        }

        // ---------- 处理推荐指令 ----------
        if (isRecommendCommand(input)) {
            addUserMessage(input);
            handleRecommendCommand();
            binding.etInput.setText("");
            return;
        }

        // ---------- 正常AI对话 ----------
        resetDetectedConfig();
        if (TextUtils.isEmpty(currentApiKey)) {
            showKeySettingCard("⚠️ 请先配置 " + getModelName(currentModel) + " 的 API Key");
            return;
        }

        addUserMessage(input);
        binding.etInput.setText("");
        binding.btnSend.setEnabled(false);

        String systemPrompt = buildSystemPrompt();
        callApi(systemPrompt, input);
    }

    // ==================== 推荐指令检测 ====================
    private boolean isRecommendCommand(String input) {
        if (TextUtils.isEmpty(input)) return false;
        String lower = input.toLowerCase();
        return lower.contains("推荐配置") || lower.contains("推荐") && lower.contains("配置")
                || lower.contains("给个配置") || lower.contains("来个接口")
                || lower.contains("随机配置") || lower.contains("配置推荐")
                || lower.contains("推荐线路");
    }

    // ==================== 处理推荐命令 ====================
    private void handleRecommendCommand() {
        if (allConfigs.isEmpty() && !isLoadingRemote) {
            isLoadingRemote = true;
            addSystemMessage("🔄 正在从互联网上搜索配置...");
            loadRemoteConfigs();
            return;
        }

        if (isLoadingRemote) {
            addSystemMessage("⏳ 配置列表加载中，请稍候...");
            return;
        }

        if (remainingConfigs.isEmpty()) {
            remainingConfigs.addAll(allConfigs);
            Collections.shuffle(remainingConfigs);
            addSystemMessage("🔄 所有配置已推荐完毕，已重新打乱列表");
        }

        int count = Math.min(5, remainingConfigs.size());
        List<ConfigItem> picked = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            picked.add(remainingConfigs.remove(0));
        }
        lastRecommended = picked;
        String reply = buildRecommendMessage(picked);
        addAiMessage(reply);
    }

    // ==================== 构建推荐消息 ====================
    private String buildRecommendMessage(List<ConfigItem> configs) {
        StringBuilder sb = new StringBuilder("🎯 为您推荐以下配置，请回复数字 **1-5** 选择加载：\n\n");
        for (int i = 0; i < configs.size(); i++) {
            ConfigItem item = configs.get(i);
            sb.append(i + 1).append(". **").append(item.name).append("**\n");
            sb.append("   `").append(item.url).append("`\n\n");
        }
        sb.append("💡 回复数字即可加载对应配置。");
        return sb.toString();
    }

    // ==================== 从远程加载配置列表 ====================
    private void loadRemoteConfigs() {
        Request request = new Request.Builder()
                .url(REMOTE_CONFIG_URL)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    isLoadingRemote = false;
                    addSystemMessage("❌ 加载远程配置失败，使用备用配置列表");
                    allConfigs.clear();
                    allConfigs.addAll(FALLBACK_CONFIGS);
                    remainingConfigs.clear();
                    remainingConfigs.addAll(allConfigs);
                    Collections.shuffle(remainingConfigs);
                    handleRecommendCommand();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (getActivity() == null) return;
                String body = response.body() != null ? response.body().string() : "";
                getActivity().runOnUiThread(() -> {
                    isLoadingRemote = false;
                    try {
                        JSONObject root = new JSONObject(body);
                        JSONArray urls = root.getJSONArray("urls");
                        List<ConfigItem> list = new ArrayList<>();
                        for (int i = 0; i < urls.length(); i++) {
                            JSONObject obj = urls.getJSONObject(i);
                            String name = obj.optString("name", "未命名");
                            String url = obj.optString("url", "");
                            if (!TextUtils.isEmpty(url)) {
                                list.add(new ConfigItem(name, url));
                            }
                        }
                        if (list.isEmpty()) {
                            throw new Exception("没有有效的配置");
                        }
                        allConfigs.clear();
                        allConfigs.addAll(list);
                        remainingConfigs.clear();
                        remainingConfigs.addAll(allConfigs);
                        Collections.shuffle(remainingConfigs);
                        addSystemMessage("✅ 成功加载 " + allConfigs.size() + " 个配置");
                        handleRecommendCommand();
                    } catch (Exception e) {
                        Log.e(TAG, "解析远程配置失败", e);
                        addSystemMessage("❌ 解析远程配置失败，使用备用配置列表");
                        allConfigs.clear();
                        allConfigs.addAll(FALLBACK_CONFIGS);
                        remainingConfigs.clear();
                        remainingConfigs.addAll(allConfigs);
                        Collections.shuffle(remainingConfigs);
                        handleRecommendCommand();
                    }
                });
            }
        });
    }

    // ==================== 系统提示词 ====================
    private String buildSystemPrompt() {
        String configTypeName;
        switch (configType) {
            case 0: configTypeName = "点播(VOD)"; break;
            case 1: configTypeName = "直播(Live)"; break;
            case 2: configTypeName = "壁纸(Wallpaper)"; break;
            default: configTypeName = "配置"; break;
        }
        return "你是一个专业的TVBox配置助手。用户需要" + configTypeName + "配置。\n" +
                "你需要从对话中理解用户的需求，并推荐合适的配置URL。\n" +
                "如果用户明确要求某个配置，或你根据对话判断出合适的配置，请在回复的最后一行以固定格式输出：\n" +
                "【配置URL】xxx\n" +
                "【配置名称】xxx\n" +
                "如果没有找到合适的配置，只输出普通回复，不要添加上述格式。\n" +
                "注意：配置URL必须是有效的http或https链接。";
    }

    // ==================== API调用 ====================
    private void callApi(String systemPrompt, String userMessage) {
        try {
            JSONObject json = new JSONObject();
            json.put("model", getModelId());

            JSONArray messagesArray = new JSONArray();
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messagesArray.put(systemMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messagesArray.put(userMsg);

            json.put("messages", messagesArray);
            json.put("max_tokens", 1024);
            json.put("temperature", 0.7);

            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    json.toString()
            );

            Request.Builder builder = new Request.Builder()
                    .url(getApiUrl())
                    .header("Content-Type", "application/json")
                    .post(body);

            builder.header("Authorization", "Bearer " + currentApiKey);

            Request request = builder.build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        addSystemMessage("❌ 网络请求失败：" + e.getMessage());
                        binding.btnSend.setEnabled(true);
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (getActivity() == null) return;
                    String responseBody = response.body() != null ? response.body().string() : "";
                    getActivity().runOnUiThread(() -> {
                        try {
                            JSONObject result = new JSONObject(responseBody);

                            if (result.has("error") || (result.has("code") && result.getInt("code") != 0)) {
                                String errorMessage = "未知错误";
                                boolean needReconfig = false;
                                if (result.has("error")) {
                                    JSONObject error = result.getJSONObject("error");
                                    errorMessage = error.optString("message", errorMessage);
                                    String errorType = error.optString("type", "");
                                    if (errorType.contains("authentication") ||
                                            errorType.contains("invalid_request_error") ||
                                            errorMessage.contains("API key") ||
                                            errorMessage.contains("credential") ||
                                            errorMessage.contains("Insufficient Balance") ||
                                            errorMessage.contains("余额不足")) {
                                        needReconfig = true;
                                    }
                                } else if (result.has("code")) {
                                    errorMessage = result.optString("message", errorMessage);
                                    if (result.getInt("code") == 401 || result.getInt("code") == 403) {
                                        needReconfig = true;
                                    }
                                }
                                addSystemMessage("❌ API错误：" + errorMessage);
                                if (needReconfig) {
                                    showKeySettingCard("请重新配置 " + getModelName(currentModel) + " 的 API Key");
                                    resetDetectedConfig();
                                }
                                binding.btnSend.setEnabled(true);
                                return;
                            }

                            JSONArray choices = result.getJSONArray("choices");
                            if (choices.length() > 0) {
                                String content = choices.getJSONObject(0)
                                        .getJSONObject("message")
                                        .getString("content");
                                parseAndAddAiResponse(content);
                            } else {
                                addSystemMessage("❌ 未获取到有效回复");
                            }
                        } catch (Exception e) {
                            String preview = responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody;
                            addSystemMessage("❌ 解析回复失败：" + e.getMessage() + "\n原始响应：" + preview);
                        }
                        binding.btnSend.setEnabled(true);
                    });
                }
            });

        } catch (Exception e) {
            addSystemMessage("❌ 请求构建失败：" + e.getMessage());
            binding.btnSend.setEnabled(true);
        }
    }

    // ==================== 解析AI响应 ====================
    private void parseAndAddAiResponse(String content) {
        detectedConfigUrl = null;
        detectedConfigName = null;

        String[] lines = content.split("\n");
        StringBuilder displayContent = new StringBuilder();

        for (String line : lines) {
            if (line.contains("【配置URL】")) {
                detectedConfigUrl = line.replace("【配置URL】", "").trim();
                if (detectedConfigUrl.startsWith("http")) {
                    displayContent.append("📎 **检测到配置URL**：").append(detectedConfigUrl).append("\n");
                } else {
                    detectedConfigUrl = null;
                }
            } else if (line.contains("【配置名称】")) {
                detectedConfigName = line.replace("【配置名称】", "").trim();
                if (!TextUtils.isEmpty(detectedConfigName)) {
                    displayContent.append("📝 **配置名称**：").append(detectedConfigName).append("\n");
                } else {
                    detectedConfigName = null;
                }
            } else {
                displayContent.append(line).append("\n");
            }
        }

        if (detectedConfigUrl == null) {
            Pattern p = Pattern.compile("https?://[^\\s<>\"']+");
            java.util.regex.Matcher m = p.matcher(content);
            if (m.find()) {
                detectedConfigUrl = m.group();
                displayContent.append("\n📎 **自动提取配置URL**：").append(detectedConfigUrl).append("\n");
            }
        }

        addAiMessage(displayContent.toString());

        if (detectedConfigUrl != null && detectedConfigUrl.startsWith("http")) {
            binding.btnAddConfig.setEnabled(true);
            binding.btnAddConfig.setText("✅ 添加此配置：" + (detectedConfigName != null ? detectedConfigName : detectedConfigUrl));
            Toast.makeText(requireContext(), "已检测到配置，点击下方按钮添加", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "检测到配置 URL: " + detectedConfigUrl);
        } else {
            binding.btnAddConfig.setEnabled(false);
            binding.btnAddConfig.setText("⚠️ 未检测到有效配置");
        }
    }

    // ==================== 添加配置到TVBox ====================
    private void addConfigToDialog() {
        if (detectedConfigUrl == null || !detectedConfigUrl.startsWith("http")) {
            Toast.makeText(requireContext(), "未检测到有效的配置链接", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "addConfigToDialog: 无效的URL，无法添加");
            return;
        }

        Log.d(TAG, "addConfigToDialog: 发送配置 URL=" + detectedConfigUrl + ", name=" + detectedConfigName);

        Bundle result = new Bundle();
        result.putString("config_url", detectedConfigUrl);
        result.putString("config_name", detectedConfigName);
        getParentFragmentManager().setFragmentResult("ai_config_result", result);

        Toast.makeText(requireContext(), "配置已发送，正在加载...", Toast.LENGTH_SHORT).show();
        addSystemMessage("✅ 配置已发送，正在加载...");

        binding.btnAddConfig.setEnabled(false);
        binding.btnAddConfig.setText("✅ 已发送");

        binding.btnAddConfig.postDelayed(this::dismiss, 1500);
    }

    // ==================== 重置状态 ====================
    private void resetDetectedConfig() {
        detectedConfigUrl = null;
        detectedConfigName = null;
        binding.btnAddConfig.setEnabled(false);
        binding.btnAddConfig.setText("⚠️ 未检测到有效配置");
    }

    // ==================== Key设置卡片显示/隐藏 ====================
    private void showKeySettingCard(String message) {
        binding.keySettingCard.setVisibility(View.VISIBLE);
        if (!TextUtils.isEmpty(currentApiKey)) {
            binding.etApiKey.setText(currentApiKey);
        } else {
            binding.etApiKey.setText("");
        }
        binding.btnSend.setEnabled(false);
        binding.etInput.setHint("请先配置 API Key");
        binding.tvKeyStatus.setText(message);
        binding.tvKeyStatus.setVisibility(View.VISIBLE);
        binding.tvKeyStatus.setTextColor(Color.BLACK);
        binding.tvGetKey.setText("📌 免费申请 " + getModelName(currentModel) + " API Key");
        resetDetectedConfig();
    }

    private void hideKeySettingCard() {
        binding.keySettingCard.setVisibility(View.GONE);
        binding.btnSend.setEnabled(true);
        binding.etInput.setHint("输入你想搜索的配置...");
        binding.tvKeyStatus.setVisibility(View.GONE);
        resetDetectedConfig();
    }

    // ==================== 消息管理 ====================
    private void addUserMessage(String text) {
        messages.add(new Message(text, true));
        adapter.notifyItemInserted(messages.size() - 1);
        binding.rvMessages.scrollToPosition(messages.size() - 1);
        saveMessages();
    }

    private void addAiMessage(String text) {
        messages.add(new Message(text, false));
        adapter.notifyItemInserted(messages.size() - 1);
        binding.rvMessages.scrollToPosition(messages.size() - 1);
        saveMessages();
    }

    private void addSystemMessage(String text) {
        messages.add(new Message("🤖 " + text, false));
        adapter.notifyItemInserted(messages.size() - 1);
        binding.rvMessages.scrollToPosition(messages.size() - 1);
        saveMessages();
    }

    // ==================== 内部类 Message ====================
    static class Message {
        String content;
        boolean isUser;
        Message(String content, boolean isUser) {
            this.content = content;
            this.isUser = isUser;
        }
    }

    // ==================== 内部类 MessageAdapter ====================
    static class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {
        private List<Message> messages;

        MessageAdapter(List<Message> messages) {
            this.messages = messages;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ai_message, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Message msg = messages.get(position);
            holder.tvContent.setText(msg.content);
            holder.tvContent.setTextColor(Color.BLACK);

            GradientDrawable drawable = new GradientDrawable();
            drawable.setCornerRadius(12);

            if (msg.isUser) {
                // 用户消息：浅灰色背景
                drawable.setColor(0xFFF0F0F0);
                holder.tvContent.setGravity(Gravity.END);
            } else {
                // AI消息：纯白背景，加浅灰色边框以区分
                drawable.setColor(Color.WHITE);
                drawable.setStroke(2, 0xFFDDDDDD);
                holder.tvContent.setGravity(Gravity.START);
            }
            holder.tvContent.setBackground(drawable);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvContent;
            ViewHolder(View itemView) {
                super(itemView);
                tvContent = itemView.findViewById(R.id.tv_message);
            }
        }
    }
}
