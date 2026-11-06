package com.example.videoparser.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Queue semantics are tested with deterministic injected parsers, never platform requests. */
public final class BatchParserTest {
    private static int checks;
    private static final VideoInfo INFO = new VideoInfo("test", "", "https://txmov2.a.kwimgs.com/video.mp4", "快手", "https://www.kuaishou.com/");

    public static void main(String[] args) throws Exception {
        String dy = "https://v.douyin.com/first/";
        String ks = "https://v.kuaishou.com/second";
        List<String> links = VideoParser.extractLinks("分享 " + dy + "\n" + ks + "。重复 " + ks, 20);
        check(links.equals(Arrays.asList(dy, ks)), "mixed platforms preserve order and deduplicate");
        check(VideoParser.extractLinks("HTTP://V.KUAISHOU.COM:443/second " + ks, 20).size() == 1, "normalized duplicate");
        String signed = ks + "?token=a%2Fb+z&x=1";
        check(VideoParser.extractLinks(signed, 20).get(0).equals(signed), "query preserved");
        check(VideoParser.extractLinks(ks + "?token=signed.", 20).get(0).endsWith("token=signed."), "do not trim signed query punctuation");
        check(VideoParser.extractLinks(dy + "，" + ks, 20).size() == 2, "Chinese punctuation separator");
        check(VideoParser.extractLinks(ks + "\n" + ks, 1).size() == 1, "duplicates do not count toward limit");
        expectIOException(() -> VideoParser.extractLinks(dy + "\n" + ks, 1), "batch max rejects overflow");
        expectIOException(() -> VideoParser.extractLinks(String.join("", Collections.nCopies(64001, "x")), 20), "input size limit");
        StringBuilder twenty = new StringBuilder();
        for (int i = 0; i < 20; i++) twenty.append("https://v.kuaishou.com/id").append(i).append('\n');
        check(VideoParser.extractLinks(twenty.toString(), 20).size() == 20, "exactly 20 accepted");
        expectIOException(() -> VideoParser.extractLinks(twenty + "https://v.kuaishou.com/overflow", 20), "21st URL rejected");

        List<String> events = new ArrayList<>();
        List<String> calls = new ArrayList<>();
        BatchParser.run(Arrays.asList("one", "bad", "three"), input -> {
            calls.add(input);
            if (input.equals("bad")) throw new IOException("模拟失败");
            return INFO;
        }, new AtomicBoolean(), (index, input, info, error) -> events.add(index + ":" + input + ":" + (info != null ? "ok" : error)));
        check(calls.equals(Arrays.asList("one", "bad", "three")), "sequential calls continue after failure");
        check(events.equals(Arrays.asList("0:one:ok", "1:bad:模拟失败", "2:three:ok")), "ordered per-item success and failure");

        calls.clear(); events.clear();
        AtomicBoolean cancelled = new AtomicBoolean();
        BatchParser.run(Arrays.asList("one", "two", "three"), input -> { calls.add(input); return INFO; }, cancelled,
                (index, input, info, error) -> { events.add(input); cancelled.set(true); });
        check(calls.size() == 1 && events.size() == 1, "cancel prevents subsequent requests");
        calls.clear();
        BatchParser.run(Arrays.asList("one", "two"), input -> { calls.add(input); return INFO; }, new AtomicBoolean(true),
                (index, input, info, error) -> { throw new AssertionError("cancelled callback"); });
        check(calls.isEmpty(), "cancel before start");

        events.clear(); cancelled.set(false);
        BatchParser.run(Arrays.asList("one", "two"), input -> { cancelled.set(true); return INFO; }, cancelled,
                (index, input, info, error) -> events.add(input));
        check(events.isEmpty(), "cancel during request discards late result");

        events.clear();
        BatchParser.run(Arrays.asList("null", "untrusted"), input -> input.equals("null") ? null
                : new VideoInfo("test", "", "https://evil.example/video.mp4"), new AtomicBoolean(),
                (index, input, info, error) -> { check(info == null && error != null, "invalid result reports failure"); events.add(input); });
        check(events.size() == 2, "invalid results do not abort queue");

        cancelled.set(false);
        BatchParser.run(Arrays.asList("one", "two"), input -> { throw new InterruptedException(); }, cancelled,
                (index, input, info, error) -> { throw new AssertionError("interrupted callback"); });
        check(cancelled.get() && Thread.currentThread().isInterrupted(), "interrupt cancels and preserves interrupt flag");
        Thread.interrupted();
        boolean rejected = false;
        try { BatchParser.run(Collections.emptyList(), input -> INFO, new AtomicBoolean(), (a,b,c,d) -> {}); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "empty queue rejected");
        check(!INFO.sourceNote.isEmpty(), "source note never promises detected watermark status");
        System.out.println("Batch parser: " + checks + " checks passed");
    }

    private interface Operation { void run() throws Exception; }
    private static void expectIOException(Operation operation, String name) throws Exception {
        boolean failed = false;
        try { operation.run(); } catch (IOException expected) { failed = true; }
        check(failed, name);
    }
    private static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }
}
