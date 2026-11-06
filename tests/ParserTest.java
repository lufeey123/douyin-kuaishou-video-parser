package com.example.videoparser.parser;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Synthetic fixtures only: no platform requests and no private media. */
public final class ParserTest {
    private static final String ID = "1234567890123456789";
    private static final String PLAY = "https://www.iesdouyin.com/aweme/v1/playwm/?video_id=abc&token=a%2Fb+z";
    private static int checks;

    public static void main(String[] args) throws Exception {
        JSONObject item = item(ID, PLAY);
        String router = "<script>window._ROUTER_DATA = {\"loaderData\":{\"video_(id)/page\":{\"videoInfoRes\":{\"item_list\":["
                + item + "]}}}};</script>";
        VideoInfo info = parse(router);
        require(info != null && info.title.equals("测试 + 标题 {带括号}"), "router title");
        require(info.videoUrl.equals(PLAY.replace("/playwm/", "/play/")), "path rewrite preserves signed query");
        require(info.coverUrl.equals("https://p3.douyinpic.com/cover.jpg"), "cover");
        String escaped = item.toString().replace("/", "\\u002F").replace("&", "\\u0026");
        require(parse("<script type=\"application/json\">" + escaped + "</script>") != null, "JSON unicode escapes");
        require(parse("<script>window.__INITIAL_STATE__ = " + item + ";</script>") != null, "initial state");
        String render = URLEncoder.encode(item.toString(), StandardCharsets.UTF_8).replace("+", "%20");
        require(parse("<script id='RENDER_DATA'>" + render + "</script>").title.contains(" + "), "URI JSON and literal plus");
        require(parse(item.toString()) != null, "raw JSON");
        JSONObject wrapper = new JSONObject().put("itemId", ID)
                .put("videoInfoRes", new JSONObject().put("item_list", new JSONArray().put(item)));
        require(parse(wrapper.toString()) != null, "route ID does not block nested video item");
        JSONObject wrongChild = new JSONObject().put("itemId", ID)
                .put("videoInfoRes", new JSONObject().put("item_list", new JSONArray().put(item("9999999999999999999", PLAY))));
        require(parse(wrongChild.toString()) == null, "route ID does not authorize different video ID");
        JSONObject alternate = item(ID, PLAY);
        JSONObject video = alternate.getJSONObject("video");
        Object address = video.remove("play_addr");
        video.put("play_addr_h264", address);
        require(parse(alternate.toString()) != null, "alternate h264 field");
        video.remove("play_addr_h264");
        video.put("bit_rate", new JSONArray().put(new JSONObject().put("play_addr", address)));
        require(parse(alternate.toString()) != null, "bitrate video address");
        JSONObject fallbackFields = item(ID, PLAY);
        fallbackFields.getJSONObject("video").getJSONObject("play_addr")
                .put("url_list", new JSONArray()).put("url", PLAY);
        require(parse(fallbackFields.toString()) != null, "empty url list falls back to url field");
        require(parse(item("9999999999999999999", PLAY).toString()) == null, "reject recommendation ID");
        require(parse(item(ID, "https://evil.example/video.mp4").toString()) == null, "reject unknown media domain");
        require(parse(item(ID, "https://127.0.0.1/video.mp4").toString()) == null, "reject private host");
        require(parse("<html>请完成验证</html>") == null, "challenge page");
        require(parse("<script>window._ROUTER_DATA={broken};</script>") == null, "malformed data");
        JSONObject fallback = item(ID, "https://evil.example/video.mp4");
        fallback.getJSONObject("video").getJSONObject("play_addr").getJSONArray("url_list").put(PLAY);
        require(parse(fallback.toString()) != null, "fallback to next trusted CDN URL");
        JSONObject imageOnly = new JSONObject().put("aweme_id", ID).put("cover", new JSONObject().put("url_list", new JSONArray().put(PLAY)));
        require(parse(imageOnly.toString()) == null, "cover is not video");
        String[] badPages = {"https://127.0.0.1/", "https://www.douyin.com.evil.example/", "http://v.douyin.com/x/", "https://user@v.douyin.com/x/"};
        for (String page : badPages) {
            boolean rejected = false;
            try { UrlPolicy.page(page); } catch (java.io.IOException expected) { rejected = true; }
            require(rejected, "reject page URL");
        }
        require(UrlPolicy.media("http://v3.douyinvod.com/video").startsWith("https://"), "upgrade old media URLs");
        System.out.println("Parser: " + checks + " checks passed");
    }

    private static JSONObject item(String id, String url) throws Exception {
        return new JSONObject().put("aweme_id", id).put("desc", "测试 + 标题 {带括号}")
                .put("video", new JSONObject()
                    .put("cover", new JSONObject().put("url_list", new JSONArray().put("https://p3.douyinpic.com/cover.jpg")))
                    .put("play_addr", new JSONObject().put("url_list", new JSONArray().put(url)).put("uri", "abc")));
    }

    private static VideoInfo parse(String html) throws Exception { return DouyinParser.parseHtml(html, ID); }
    private static void require(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }
}
