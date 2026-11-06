package com.example.videoparser.parser;

/** Immutable result; an empty coverUrl means that no usable cover was found. */
public final class VideoInfo {
    public final String title;
    public final String coverUrl;
    public final String videoUrl;
    public final String platform;
    public final String referer;
    public final String sourceNote;

    public VideoInfo(String title, String coverUrl, String videoUrl) {
        this(title, coverUrl, videoUrl, "抖音", "https://www.iesdouyin.com/");
    }

    public VideoInfo(String title, String coverUrl, String videoUrl, String platform, String referer) {
        this(title, coverUrl, videoUrl, platform, referer, "平台播放源 · 画面水印请预览确认");
    }

    public VideoInfo(String title, String coverUrl, String videoUrl, String platform, String referer, String sourceNote) {
        this.platform = "快手".equals(platform) ? "快手" : "抖音";
        // Request headers come from a fixed platform mapping, never page-provided data.
        this.referer = "快手".equals(this.platform) ? "https://www.kuaishou.com/" : "https://www.iesdouyin.com/";
        this.title = title == null || title.trim().isEmpty() ? this.platform + "视频" : title;
        this.coverUrl = coverUrl == null ? "" : coverUrl;
        this.videoUrl = videoUrl;
        this.sourceNote = sourceNote == null || sourceNote.trim().isEmpty()
                ? "平台播放源 · 画面水印请预览确认" : sourceNote;
    }
}
