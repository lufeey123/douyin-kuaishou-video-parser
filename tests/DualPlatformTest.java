package com.example.videoparser.parser;

import org.json.JSONArray;
import org.json.JSONObject;

/** Matches the shape observed in a real public Kuaishou share page, with synthetic URLs. */
public final class DualPlatformTest {
    private static final String ID = "3xisedtztszdgyg";
    private static final String MEDIA = "https://txmov2.a.kwimgs.com/upic/video.mp4?token=a%2Fb+z&x=1";
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(VideoParser.isKuaishouHost(VideoParser.extractLink("快手 https://v.kuaishou.com/J9rbr4eV 热点").getHost()), "Kuaishou short link routing");
        check(VideoParser.isKuaishouHost(VideoParser.extractLink("https://v.m.chenzhongtech.com/fw/long-video/" + ID).getHost()), "Kuaishou canonical routing");
        check(!VideoParser.isKuaishouHost(VideoParser.extractLink("https://v.douyin.com/test/").getHost()), "Douyin routing preserved");
        check(VideoParser.extractLink("http://v.kuaishou.com/test").getScheme().equals("https"), "upgrade HTTP input");
        check(VideoParser.extractLink("https://evil.example/ https://v.kuaishou.com/test。").getHost().equals("v.kuaishou.com"), "skip unrelated URLs and trailing punctuation");
        for (String value : new String[]{"https://v.kuaishou.com.evil.example/test", "https://user@v.kuaishou.com/test", "https://127.0.0.1/test", "https://v.kuaishou.com:444/test", "nothing"}) {
            boolean rejected = false;
            try { VideoParser.extractLink(value); } catch (java.io.IOException expected) { rejected = true; }
            check(rejected, "reject unsupported share input");
        }
        JSONObject photo = photo(ID, MEDIA);
        VideoInfo info = parse(photo);
        check(info != null, "INIT_STATE photo parsed");
        check(info.title.equals("快手测试 + 标题"), "caption preserved");
        check(info.videoUrl.equals(MEDIA), "signed URL unchanged");
        check(info.platform.equals("快手") && info.referer.equals("https://www.kuaishou.com/"), "Kuaishou request context");
        check(info.coverUrl.equals("https://p23.a.yximgs.com/cover.jpg"), "cover parsed");
        check(parse(photo("otherPhotoId", MEDIA)) == null, "do not select recommendation");
        check(parse(photo(ID, "https://evil.example/video.mp4")) == null, "reject unrelated media");
        check(parse(photo(ID, "https://127.0.0.1/video.mp4")) == null, "reject local media");
        JSONObject fallback = photo(ID, "https://evil.example/video.mp4");
        fallback.getJSONArray("mainMvUrls").put(new JSONObject().put("url", MEDIA));
        check(parse(fallback) != null, "next trusted CDN candidate");
        JSONObject imageOnly = photo(ID, MEDIA);
        imageOnly.remove("mainMvUrls");
        check(parse(imageOnly) == null, "cover is not video");
        JSONObject manifestOnly = photo(ID, MEDIA);
        manifestOnly.remove("mainMvUrls");
        JSONObject avc = representation("avc", 960, 720, MEDIA);
        JSONObject hevc = representation("hevc", 1440, 1080, "https://txmov2.a.kwimgs.com/high.mp4");
        JSONObject manifest = new JSONObject().put("adaptationSet", new JSONArray().put(
                new JSONObject().put("representation", new JSONArray().put(hevc).put(avc))));
        manifestOnly.put("manifest", manifest);
        check(parse(manifestOnly).videoUrl.equals(MEDIA), "manifest fallback prefers compatible AVC and preserves signature");
        check(parse(manifestOnly).sourceNote.contains("预览"), "source note does not claim watermark detection");
        manifestOnly.put("manifest", manifest.toString());
        check(parse(manifestOnly) != null, "string-encoded manifest fallback");
        avc.put("hidden", true);
        manifestOnly.put("manifest", manifest);
        check(parse(manifestOnly).videoUrl.contains("high.mp4"), "skip hidden representations");
        hevc.put("url", "https://evil.example/video.mp4").put("backupUrl", new JSONArray().put(MEDIA));
        check(parse(manifestOnly).videoUrl.equals(MEDIA), "manifest backup trusted CDN URL");
        hevc.put("width", 0).put("height", 0);
        check(parse(manifestOnly) == null, "do not treat audio-only manifest as video");
        String unicode = "<script>window.INIT_STATE = " + new JSONObject().put("photo", photo).toString()
                .replace("/", "\\u002F").replace("&", "\\u0026") + ";</script>";
        check(KuaishouParser.parseHtml(unicode, ID).videoUrl.equals(MEDIA), "JSON escape decoding");
        check(KuaishouParser.parseHtml("<html>验证后查看</html>", ID) == null, "no fabricated challenge result");
        check(new VideoInfo("", "", MEDIA).platform.equals("抖音"), "legacy Douyin model compatibility");
        System.out.println("Dual platform: " + checks + " checks passed");
    }

    private static JSONObject photo(String id, String url) throws Exception {
        return new JSONObject().put("photoId", 5195465325116027286L)
            .put("share_info", "userId=test&photoId=" + id)
            .put("caption", "快手测试 + 标题")
            .put("mainMvUrls", new JSONArray().put(new JSONObject().put("url", url)))
            .put("coverUrls", new JSONArray().put(new JSONObject().put("url", "https://p23.a.yximgs.com/cover.jpg")));
    }
    private static String html(JSONObject photo) throws Exception {
        return "<script>window.INIT_STATE = " + new JSONObject().put("/fw/long-video/" + ID, new JSONObject().put("photo", photo)) + ";</script>";
    }
    private static JSONObject representation(String codec, int width, int height, String url) throws Exception {
        return new JSONObject().put("videoCodec", codec).put("width", width).put("height", height).put("url", url);
    }
    private static VideoInfo parse(JSONObject photo) throws Exception { return KuaishouParser.parseHtml(html(photo), ID); }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
