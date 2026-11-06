package com.example.videoparser.parser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** Parses data published in share pages. Never executes JavaScript or bypasses verification. */
public final class DouyinParser {
    public static final String USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1";
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final Pattern LINK = Pattern.compile("https://[A-Za-z0-9.-]+(?::[0-9]+)?[^\\s<>\\\"“”]*");
    private static final Pattern ID_PATH = Pattern.compile("/(?:video|note)/([0-9]{8,30})(?:/|$)");
    private static final Pattern LAST_PATH = Pattern.compile("\"lastPath\"\\s*:\\s*\"([0-9]{8,30})\"");
    private static final Pattern SCRIPT = Pattern.compile("<script\\b([^>]*)>(.*?)</script\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern RENDER_ID = Pattern.compile("\\bid\\s*=\\s*(['\"])RENDER_DATA\\1", Pattern.CASE_INSENSITIVE);
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);

    public VideoInfo parse(String input) throws Exception {
        if (input == null || input.trim().isEmpty()) throw new IOException("请粘贴抖音分享文本或链接");
        if (input.length() > 16000) throw new IOException("分享文本过长");
        URI share = extractLink(input);
        Page first = fetch(share);
        String id = extractId(first.uri, first.html);
        if (id.isEmpty()) id = extractId(share, first.html);
        if (id.isEmpty()) throw new IOException("未找到视频 ID；请确认分享链接有效，且内容为视频");
        VideoInfo result = parseHtml(first.html, id);
        if (result != null) return result;
        URI canonical = UrlPolicy.page("https://www.iesdouyin.com/share/video/" + id + "/");
        // Retry with cookies established by the first request, even at the same URL.
        Page page = fetch(canonical);
        result = parseHtml(page.html, id);
        if (result != null) return result;
        if (!page.html.contains("play_addr") && !page.html.contains("playAddr") && !page.html.contains("playApi")) {
            throw new IOException("分享页未返回视频播放数据，当前版本无法解析此链接。在抖音能播放，不代表分享网页提供了播放地址。");
        }
        throw new IOException("已获取分享页，但未能识别出该作品的有效播放地址。请反馈完整分享链接以便适配。");
    }

    private static URI extractLink(String input) throws IOException {
        Matcher matcher = LINK.matcher(input);
        while (matcher.find()) {
            String candidate = matcher.group().replaceAll("[，。！？、；：）)\\]}]+$", "");
            try { return UrlPolicy.page(candidate); } catch (IOException ignored) { }
        }
        throw new IOException("未找到支持的 HTTPS 抖音链接");
    }

    private static String extractId(URI uri, String html) {
        Matcher path = ID_PATH.matcher(uri.getPath() == null ? "" : uri.getPath());
        if (path.find()) return path.group(1);
        String query = uri.getRawQuery();
        if (query != null) {
            Matcher q = Pattern.compile("(?:^|&)(?:modal_id|item_id|aweme_id)=([0-9]{8,30})(?:&|$)").matcher(query);
            if (q.find()) return q.group(1);
        }
        Matcher last = LAST_PATH.matcher(html);
        return last.find() ? last.group(1) : "";
    }

    /** Package-visible for fixture tests; an unavailable address returns null. */
    static VideoInfo parseHtml(String html, String wantedId) throws JSONException {
        List<Object> roots = new ArrayList<>();
        Matcher scripts = SCRIPT.matcher(html);
        while (scripts.find()) {
            String content = scripts.group(2).trim();
            if (RENDER_ID.matcher(scripts.group(1)).find()) {
                try { addJson(roots, URLDecoder.decode(content.replace("+", "%2B"), "UTF-8")); }
                catch (Exception ignored) { /* malformed JSON/encoding is not executable data */ }
            } else if (content.startsWith("{") || content.startsWith("[")) {
                addJson(roots, content);
            }
            for (String name : new String[]{"_ROUTER_DATA", "__INITIAL_STATE__"}) {
                int offset = content.indexOf(name);
                if (offset < 0) continue;
                int equals = content.indexOf('=', offset + name.length());
                if (equals < 0) continue;
                String json = balancedJson(content, equals + 1);
                if (json != null) addJson(roots, json);
            }
        }
        // Some endpoints return a JSON response rather than HTML.
        String trimmed = html.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) addJson(roots, trimmed);
        Budget budget = new Budget();
        for (Object root : roots) {
            VideoInfo info = findItem(root, wantedId, 0, budget);
            if (info != null) return info;
        }
        return null;
    }

    private static void addJson(List<Object> roots, String json) {
        if (roots.size() >= 32) return;
        try {
            Object value = new JSONTokener(json).nextValue();
            if (value instanceof JSONObject || value instanceof JSONArray) roots.add(value);
        } catch (JSONException ignored) { }
    }

    private static String balancedJson(String text, int start) {
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
        if (start >= text.length() || (text.charAt(start) != '{' && text.charAt(start) != '[')) return null;
        boolean quoted = false, escape = false;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (escape) escape = false;
                else if (c == '\\') escape = true;
                else if (c == '"') quoted = false;
            } else {
                if (c == '"') quoted = true;
                else if (c == '{' || c == '[') depth++;
                else if (c == '}' || c == ']') {
                    if (--depth == 0) return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private static VideoInfo findItem(Object node, String wantedId, int depth, Budget budget) throws JSONException {
        if (depth > 64 || ++budget.nodes > 60000) return null;
        if (node instanceof JSONObject) {
            JSONObject item = (JSONObject) node;
            String id = item.optString("aweme_id", item.optString("awemeId", item.optString("item_id", item.optString("itemId", ""))));
            if (wantedId.equals(id)) {
                JSONObject video = item.optJSONObject("video");
                if (video != null) {
                    String url = firstUrl(video.opt("play_addr"));
                    if (url.isEmpty()) url = firstUrl(video.opt("playAddr"));
                    if (url.isEmpty()) url = firstUrl(video.opt("playApi"));
                    if (url.isEmpty()) url = firstUrl(video.opt("play_addr_h264"));
                    if (url.isEmpty()) url = firstUrl(video.opt("play_addr_265"));
                    if (url.isEmpty()) {
                        JSONArray rates = video.optJSONArray("bit_rate");
                        if (rates != null) {
                            for (int i = 0; i < rates.length() && url.isEmpty(); i++) {
                                JSONObject rate = rates.optJSONObject(i);
                                if (rate != null) url = firstUrl(rate.opt("play_addr"));
                            }
                        }
                    }
                    if (!url.isEmpty()) {
                        try {
                            url = withoutWatermark(UrlPolicy.media(url));
                            String cover = firstUrl(video.opt("cover"));
                            if (cover.isEmpty()) cover = firstUrl(video.opt("origin_cover"));
                            try { cover = cover.isEmpty() ? "" : UrlPolicy.media(cover); }
                            catch (IOException ignored) { cover = ""; }
                            return new VideoInfo(item.optString("desc", item.optString("title", "抖音视频")), cover, url);
                        } catch (IOException ignored) { /* reject untrusted URLs */ }
                    }
                }
                // A route wrapper can have itemId without owning video data.
                // Continue; every descendant must independently match wantedId.
            }
            Iterator<String> keys = item.keys();
            while (keys.hasNext()) {
                VideoInfo info = findItem(item.opt(keys.next()), wantedId, depth + 1, budget);
                if (info != null) return info;
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                VideoInfo info = findItem(array.opt(i), wantedId, depth + 1, budget);
                if (info != null) return info;
            }
        }
        return null;
    }

    private static String firstUrl(Object value) {
        if (value instanceof String) {
            try { return UrlPolicy.media((String) value); }
            catch (IOException ignored) { return ""; }
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String url = firstUrl(object.opt("url_list"));
            if (url.isEmpty()) url = firstUrl(object.opt("urlList"));
            if (url.isEmpty()) url = firstUrl(object.opt("url"));
            return url;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                String url = firstUrl(array.opt(i));
                if (!url.isEmpty()) return url;
            }
        }
        return "";
    }

    private static String withoutWatermark(String value) throws IOException {
        URI uri = URI.create(value);
        String path = uri.getRawPath();
        if (path == null || !path.contains("/playwm/")) return value;
        String replaced = "https://" + uri.getRawAuthority() + path.replace("/playwm/", "/play/");
        if (uri.getRawQuery() != null) replaced += "?" + uri.getRawQuery();
        return UrlPolicy.media(replaced);
    }

    private Page fetch(URI initial) throws IOException {
        URI uri = initial;
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("解析已取消");
            uri = UrlPolicy.page(uri.toString());
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "text/html,application/json");
            connection.setRequestProperty("Accept-Encoding", "gzip");
            for (Map.Entry<String, List<String>> header : cookies.get(uri, java.util.Collections.emptyMap()).entrySet()) {
                connection.setRequestProperty(header.getKey(), String.join("; ", header.getValue()));
            }
            try {
                int code = connection.getResponseCode();
                cookies.put(uri, connection.getHeaderFields());
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("分享链接重定向缺少目标地址");
                    try { uri = UrlPolicy.page(uri.resolve(location).toString()); }
                    catch (IllegalArgumentException e) { throw new IOException("重定向地址无效", e); }
                    continue;
                }
                if (code != 200) throw new IOException("页面请求失败（HTTP " + code + "）");
                if (connection.getContentLengthLong() > MAX_BYTES) throw new IOException("页面内容过大");
                InputStream raw = connection.getInputStream();
                try (InputStream stream = "gzip".equalsIgnoreCase(connection.getContentEncoding()) ? new GZIPInputStream(raw) : raw;
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = stream.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new IOException("解析已取消");
                        if (output.size() + count > MAX_BYTES) throw new IOException("页面内容过大");
                        output.write(buffer, 0, count);
                    }
                    return new Page(uri, new String(output.toByteArray(), StandardCharsets.UTF_8));
                }
            } finally { connection.disconnect(); }
        }
        throw new IOException("分享链接重定向次数过多");
    }

    private static final class Page {
        final URI uri;
        final String html;
        Page(URI uri, String html) { this.uri = uri; this.html = html; }
    }
    private static final class Budget { int nodes; }
}
