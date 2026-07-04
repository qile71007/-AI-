package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.BuildConfig;

public class Github {

    private static final String GITHUB_API = "https://api.github.com/repos/qile71007/webhtv";
    private static final String GITHUB_RELEASE = "https://github.com/qile71007/webhtv/releases/download";
    private static final String CNB_API = "https://gitee.com/qile71007/webhtv/releases/download";

    private Github() {
        // 私有构造方法，防止实例化
    }

    /**
     * 获取 CNB（国内镜像）更新资源下载链接
     * @param fileName 文件名（如：manifest.json）
     * @return 完整的下载链接
     */
    public static String getCnbAsset(String fileName) {
        if (TextUtils.isEmpty(fileName)) return "";
        return CNB_API + "/" + fileName;
    }

    /**
     * 获取 GitHub 最新版本的 manifest 下载链接
     * @param fileName 文件名（如：manifest.json）
     * @return 完整的下载链接
     */
    public static String getGithubLatestAsset(String fileName) {
        return GITHUB_API + "/releases/latest";
    }

    /**
     * 获取 GitHub Releases API 地址
     * @return GitHub Releases API URL
     */
    public static String getReleasesApi() {
        return GITHUB_API + "/releases";
    }

    /**
     * 获取指定版本 tag 的 GitHub Release 下载链接
     * @param tag 版本标签（如：v1.0.0）
     * @param fileName 文件名（如：app-release.apk）
     * @return 完整的下载链接
     */
    public static String getGithubReleaseAsset(String tag, String fileName) {
        if (TextUtils.isEmpty(tag) || TextUtils.isEmpty(fileName)) return "";
        return GITHUB_RELEASE + "/" + tag + "/" + fileName;
    }

    /**
     * 获取指定版本 tag 的 GitHub Release 信息 API 地址
     * @param tag 版本标签（如：v1.0.0）
     * @return Release 信息 API URL
     */
    public static String getReleaseApi(String tag) {
        if (TextUtils.isEmpty(tag)) return "";
        return GITHUB_API + "/releases/tags/" + tag;
    }

    /**
     * 获取最新版本 tag
     * @return 最新版本标签
     */
    public static String getLatestTag() {
        return GITHUB_API + "/releases/latest";
    }

    /**
     * 获取 GitHub 的 Download URL
     * @param fileName 文件名
     * @return 下载链接
     */
    public static String getDownloadUrl(String fileName) {
        if (TextUtils.isEmpty(fileName)) return "";
        return GITHUB_RELEASE + "/" + fileName;
    }

    /**
     * 获取 GitHub 仓库的 tags 列表
     * @return tags 列表 API URL
     */
    public static String getTagsApi() {
        return GITHUB_API + "/tags";
    }

}
