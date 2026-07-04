package com.fongmi.android.tv.playback;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlaybackProgressDeleteInput {

    @SerializedName("historyKey")
    public String historyKey;
    @SerializedName("siteKey")
    public String siteKey;
    @SerializedName("vodId")
    public String vodId;
    @SerializedName("episodeName")
    public String episodeName;
    @SerializedName("scope")
    public String scope;
    @SerializedName("cid")
    public int cid;
    @SerializedName("configKey")
    public String configKey;
    @SerializedName("configUrl")
    public String configUrl;
    @SerializedName("confirm")
    public boolean confirm;

    public PlaybackProgressDeleteInput normalize() {
        historyKey = safe(historyKey);
        siteKey = safe(siteKey);
        vodId = safe(vodId);
        episodeName = safe(episodeName);
        configKey = PlaybackConfigIdentity.normalizeKey(configKey);
        configUrl = safe(configUrl);
        if (TextUtils.isEmpty(configKey) && !TextUtils.isEmpty(configUrl)) configKey = PlaybackConfigIdentity.keyForUrl(configUrl);
        scope = safe(scope).toLowerCase();
        return this;
    }

    public boolean isAllScope() {
        normalize();
        return "all".equals(scope);
    }

    public boolean isSiteScope() {
        normalize();
        return "site".equals(scope);
    }

    public static PlaybackProgressDeleteInput fromJson(JsonObject object) {
        PlaybackProgressDeleteInput input = App.gson().fromJson(object, PlaybackProgressDeleteInput.class);
        if (input == null) input = new PlaybackProgressDeleteInput();
        applyAliases(input, object);
        return input.normalize();
    }

    public static List<PlaybackProgressDeleteInput> listFromJson(String text) {
        if (TextUtils.isEmpty(text)) return Collections.emptyList();
        JsonElement element = JsonParser.parseString(text);
        if (element == null || element.isJsonNull()) return Collections.emptyList();
        JsonArray array = asArray(element);
        if (array == null) return element.isJsonObject() ? Collections.singletonList(fromJson(element.getAsJsonObject())) : Collections.emptyList();
        List<PlaybackProgressDeleteInput> inputs = new ArrayList<>();
        for (JsonElement item : array) if (item != null && item.isJsonObject()) inputs.add(fromJson(item.getAsJsonObject()));
        return inputs;
    }

    private static void applyAliases(PlaybackProgressDeleteInput input, JsonObject object) {
        input.historyKey = firstString(input.historyKey, object, "key");
        input.siteKey = firstString(input.siteKey, object, "site", "site_key");
        input.configKey = firstString(input.configKey, object, "config_key", "interfaceKey", "sourceConfigKey");
        input.configUrl = firstString(input.configUrl, object, "config_url", "interfaceUrl", "sourceConfigUrl");
        input.vodId = firstString(input.vodId, object, "vod_id", "videoId", "itemId");
        input.episodeName = firstString(input.episodeName, object, "episode", "episodeTitle", "vodRemarks", "remarks");
    }

    private static JsonArray asArray(JsonElement element) {
        if (element.isJsonArray()) return element.getAsJsonArray();
        if (!element.isJsonObject()) return null;
        JsonObject object = element.getAsJsonObject();
        for (String key : new String[]{"items", "records", "data", "list"}) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonArray()) return value.getAsJsonArray();
        }
        return null;
    }

    private static String firstString(String current, JsonObject object, String... keys) {
        if (!TextUtils.isEmpty(current)) return current;
        for (String key : keys) {
            try {
                JsonElement value = object.get(key);
                if (value != null && !value.isJsonNull()) return value.getAsString();
            } catch (Exception ignored) {
            }
        }
        return current;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
