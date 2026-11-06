package com.example.videoparser;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import com.example.videoparser.parser.VideoInfo;
import com.example.videoparser.parser.VideoParser;

/** Saves through the system download service; enqueue is not a completion signal. */
public final class DownloadHelper {
    private DownloadHelper() {}

    public static long enqueue(Context context, VideoInfo info) throws Exception {
        if (info == null || !NetworkPolicy.isMediaUrl(info.videoUrl)) {
            throw new IllegalArgumentException("视频地址无效，请重新解析");
        }
        String title = info.title == null ? "视频" : info.title;
        title = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (title.isEmpty()) title = "视频";
        // Bound UTF-8 filename size even when the title includes emoji.
        int count = title.codePointCount(0, title.length());
        if (count > 48) title = title.substring(0, title.offsetByCodePoints(0, 48));
        String filename = title + "_" + System.currentTimeMillis() + ".mp4";
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) throw new IllegalStateException("系统下载服务不可用");
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(info.videoUrl));
        request.setTitle(title);
        request.setDescription("拾影 · 视频下载");
        request.addRequestHeader("Referer", info.referer);
        request.addRequestHeader("User-Agent", VideoParser.USER_AGENT);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
        return manager.enqueue(request);
    }
}
