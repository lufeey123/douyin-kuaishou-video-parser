package com.example.videoparser.parser;

import com.example.videoparser.NetworkPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** A bounded sequential queue. Does not own threads, retain a UI, or download files. */
public final class BatchParser {
    private BatchParser() {}

    public interface ItemParser { VideoInfo parse(String input) throws Exception; }
    public interface Listener { void onItem(int index, String input, VideoInfo info, String error); }

    public static void run(List<String> inputs, ItemParser parser, AtomicBoolean cancelled, Listener listener) {
        if (inputs == null || parser == null || cancelled == null || listener == null) {
            throw new IllegalArgumentException("批量参数不能为空");
        }
        List<String> queue = new ArrayList<>(inputs);
        if (queue.isEmpty() || queue.size() > VideoParser.MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("一次可解析 1 至 20 条链接");
        }
        for (int index = 0; index < queue.size(); index++) {
            if (cancelled.get() || Thread.currentThread().isInterrupted()) return;
            String input = queue.get(index);
            VideoInfo info = null;
            String error = null;
            try {
                info = parser.parse(input);
                if (info == null || !NetworkPolicy.isMediaUrl(info.videoUrl)) {
                    info = null;
                    error = "未获取到有效的视频地址";
                }
            } catch (InterruptedException interrupted) {
                cancelled.set(true);
                Thread.currentThread().interrupt();
                return;
            } catch (Exception failure) {
                error = failure.getMessage();
                if (error == null || error.trim().isEmpty()) error = "解析失败，请检查网络或作品是否公开";
            }
            // Cancelling during an in-flight request discards its late result.
            if (cancelled.get() || Thread.currentThread().isInterrupted()) return;
            listener.onItem(index, input, info, error);
        }
    }
}
