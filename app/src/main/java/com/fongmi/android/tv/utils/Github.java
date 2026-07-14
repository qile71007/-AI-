package com.fongmi.android.tv.utils;

import android.text.TextUtils;

public class Github {

    private static final String GITHUB_API = "https://api.github.com/repos/qile71007/-AI-";
    private static final String GITHUB_RELEASE = "https://github.com/qile71007/-AI-/releases/download";
    private static final String CNB_API = "https://gitee.com/qile71007/-AI-/releases/download";

    private Github() {
        // 私有构造方法，防止实例化
    }

    // ========== 新增：获取最新 Release 的 API 地址（修复编译错误） ==========
    public static String getLatestReleaseApi() {
        return GITHUB_API + "/releases/latest";
    }

    // ========== 获取 CNB（国内镜像）资源链接 ==========
    // 用于 manifest 获取（使用 “latest” 作为 tag 重定向到最新版本）
    public static String getCnbAsset(String fileName) {
        if (TextUtils.isEmpty(fileName)) return "";
        return CNB_API + "/latest/" + fileName;
    }

    // 用于 APK 下载（带具体 tag）
    public static String getCnbAsset(String tag, String fileName) {
        if (TextUtils.isEmpty(tag) || TextUtils.isEmpty(fileName)) return "";
        return CNB_API + "/" + tag + "/" + fileName;
    }

    // ========== GitHub 相关方法 ==========
    public static String getGithubLatestAsset(String fileName) {
        return GITHUB_API + "/releases/latest";
    }

    public static String getReleasesApi() {
        return GITHUB_API + "/releases";
    }

    public static String getGithubReleaseAsset(String tag, String fileName) {
        if (TextUtils.isEmpty(tag) || TextUtils.isEmpty(fileName)) return "";
        return GITHUB_RELEASE + "/" + tag + "/" + fileName;
    }

    public static String getReleaseApi(String tag) {
        if (TextUtils.isEmpty(tag)) return "";
        return GITHUB_API + "/releases/tags/" + tag;
    }

    public static String getLatestTag() {
        return GITHUB_API + "/releases/latest";
    }

    public static String getDownloadUrl(String fileName) {
        if (TextUtils.isEmpty(fileName)) return "";
        return GITHUB_RELEASE + "/" + fileName;
    }

    public static String getTagsApi() {
        return GITHUB_API + "/tags";
    }
}
