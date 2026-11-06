package com.example.videoparser.parser;

import com.example.videoparser.NetworkPolicy;
import org.json.JSONArray;
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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** Reads the public share page. Does not execute scripts or bypass login/challenges. */
public final class KuaishouParser {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final Pattern LINK = Pattern.compile("https://[A-Za-z0-9.-]+(?::[0-9]+)?[^\\s<>\\\"“”]*");
    private static final Pattern SCRIPT = Pattern.compile("<script\\b[^>]*>(.*?)</script\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PATH_ID = Pattern.compile("/(?:long-video|short-video|photo)/([A-Za-z0-9_-]{5,64})(?:/|$)");
    private static final Pattern ASSIGNMENT = Pattern.compile("(?:window\\s*\\.\\s*)?(?:INIT_STATE|__INIT_STATE__|__INITIAL_STATE__|__APOLLO_STATE__)\\s*=");
    private static final String USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1";
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);

    public VideoInfo parse(String input) throws Exception {
        if (input == null || input.trim().isEmpty()) throw new IOException("请粘贴快手分享文本或链接");
        if (input.length() > 16000) throw new IOException("分享文本过长");
        URI share = extractLink(input);
        Page first = fetch(share);
        String id = pageId(first.uri);
        if (id.isEmpty()) id = pageId(share);
        if (id.isEmpty()) throw new IOException("未找到快手作品 ID，请确认是公开的视频分享链接");
        VideoInfo info = parseHtml(first.html, id);
        if (info != null) return info;
        // A second request may use cookies established by the share redirect/page.
        Page second = fetch(first.uri);
        info = parseHtml(second.html, id);
        if (info != null) return info;
        throw new IOException("快手分享页未提供可识别的视频地址，可能为图集、失效作品或需要在浏览器中加载");
    }

    private static URI extractLink(String input) throws IOException {
        Matcher matcher = LINK.matcher(input);
        while (matcher.find()) {
            String candidate = matcher.group().replaceAll("[，。！？、；：）)\\]}]+$", "");
            try { return pageUri(candidate); } catch (IOException ignored) { }
        }
        throw new IOException("未找到支持的 HTTPS 快手分享链接");
    }

    private static String pageId(URI uri) {
        Matcher path = PATH_ID.matcher(uri.getPath() == null ? "" : uri.getPath());
        if (path.find()) return path.group(1);
        return queryValue(uri.getRawQuery(), "photoId");
    }

    private static String queryValue(String query, String key) {
        if (query == null) return "";
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0 && pair.substring(0, equals).equals(key)) {
                try { return URLDecoder.decode(pair.substring(equals + 1), "UTF-8"); }
                catch (Exception ignored) { return ""; }
            }
        }
        return "";
    }

    /** Package-visible for fixtures and testing captured public HTML without network requests. */
    static VideoInfo parseHtml(String html, String wantedId) throws Exception {
        if (html == null || wantedId == null || wantedId.isEmpty() || html.length() > MAX_BYTES) return null;
        List<Object> roots = new ArrayList<>();
        Matcher scripts = SCRIPT.matcher(html);
        while (scripts.find() && roots.size() < 32) {
            String content = scripts.group(1).trim();
            if (content.startsWith("{") || content.startsWith("[")) addJson(roots, content);
            Matcher assignments = ASSIGNMENT.matcher(content);
            while (assignments.find() && roots.size() < 32) {
                String json = balancedJson(content, assignments.end());
                if (json != null) addJson(roots, json);
            }
        }
        String trimmed = html.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) addJson(roots, trimmed);
        Budget budget = new Budget();
        for (Object root : roots) {
            VideoInfo info = findPhoto(root, wantedId, 0, budget);
            if (info != null) return info;
        }
        return null;
    }

    private static void addJson(List<Object> roots, String text) {
        if (roots.size() >= 32) return;
        try {
            Object value = new JSONTokener(text).nextValue();
            if (value instanceof JSONObject || value instanceof JSONArray) roots.add(value);
        } catch (Exception ignored) { }
    }

    private static String balancedJson(String text, int start) {
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
        if (start == text.length() || (text.charAt(start) != '{' && text.charAt(start) != '[')) return null;
        boolean string = false, escape = false;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (string) {
                if (escape) escape = false;
                else if (c == '\\') escape = true;
                else if (c == '"') string = false;
            } else {
                if (c == '"') string = true;
                else if (c == '{' || c == '[') depth++;
                else if ((c == '}' || c == ']') && --depth == 0) return text.substring(start, i + 1);
            }
        }
        return null;
    }

    private static VideoInfo findPhoto(Object node, String wantedId, int depth, Budget budget) {
        if (depth > 64 || ++budget.nodes > 60000) return null;
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            if (matchesPhoto(object, wantedId) && !object.optBoolean("singlePicture", false)
                    && !"IMAGE".equalsIgnoreCase(object.optString("photoType"))) {
                String video = "";
                for (String key : new String[]{"mainMvUrls", "mainMvUrl", "photoUrl", "videoUrl", "playUrl"}) {
                    video = mediaUrl(object.opt(key));
                    if (!video.isEmpty()) break;
                }
                JSONObject videoObject = object.optJSONObject("video");
                if (video.isEmpty() && videoObject != null) {
                    video = mediaUrl(videoObject.opt("playUrl"));
                    if (video.isEmpty()) video = mediaUrl(videoObject.opt("url"));
                }
                // A public page may expose only its playback manifest. Select an
                // actual CDN URL; never fabricate a "clean" URL by changing signatures.
                if (video.isEmpty()) video = manifestUrl(object.opt("manifest"));
                if (!video.isEmpty()) {
                    String cover = "";
                    for (String key : new String[]{"coverUrls", "coverUrl", "cover", "webpCoverUrls"}) {
                        cover = mediaUrl(object.opt(key));
                        if (!cover.isEmpty()) break;
                    }
                    String title = object.optString("caption", object.optString("title", "快手视频"));
                    return new VideoInfo(title, cover, video, "快手", "https://www.kuaishou.com/",
                            "快手播放源 · 水印请预览确认");
                }
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                VideoInfo info = findPhoto(object.opt(keys.next()), wantedId, depth + 1, budget);
                if (info != null) return info;
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                VideoInfo info = findPhoto(array.opt(i), wantedId, depth + 1, budget);
                if (info != null) return info;
            }
        }
        return null;
    }

    private static boolean matchesPhoto(JSONObject photo, String wantedId) {
        // Public share IDs differ from numeric photoId in current INIT_STATE data.
        String sharedId = queryValue(photo.optString("share_info", ""), "photoId");
        if (!sharedId.isEmpty()) return wantedId.equals(sharedId);
        return wantedId.equals(photo.optString("photoId", ""))
                || wantedId.equals(photo.optString("id", ""));
    }

    private static String manifestUrl(Object value) {
        JSONObject manifest = value instanceof JSONObject ? (JSONObject) value : null;
        if (manifest == null && value instanceof String && ((String) value).length() <= MAX_BYTES) {
            try { manifest = new JSONObject((String) value); } catch (Exception ignored) { }
        }
        if (manifest == null) return "";
        JSONArray sets = manifest.optJSONArray("adaptationSet");
        if (sets == null) return "";
        String selected = "";
        long best = -1;
        for (int i = 0; i < sets.length() && i < 16; i++) {
            JSONObject set = sets.optJSONObject(i);
            if (set == null) continue;
            JSONArray versions = set.optJSONArray("representation");
            if (versions == null) continue;
            for (int j = 0; j < versions.length() && j < 64; j++) {
                JSONObject version = versions.optJSONObject(j);
                if (version == null || version.optBoolean("hidden", false)) continue;
                // Audio-only representations are not downloadable video results.
                if (version.optInt("width", 0) <= 0 || version.optInt("height", 0) <= 0) continue;
                String url = mediaUrl(version.opt("url"));
                if (url.isEmpty()) url = mediaUrl(version.opt("backupUrl"));
                if (url.isEmpty()) continue;
                String codec = version.optString("videoCodec", "").toLowerCase(Locale.ROOT);
                long pixels = Math.max(0, Math.min(16384, version.optInt("width", 0)))
                        * (long) Math.max(0, Math.min(16384, version.optInt("height", 0)));
                // Prefer AVC for Android/player compatibility, then resolution.
                long score = pixels + ((codec.contains("avc") || codec.contains("h264")) ? 1_000_000_000L : 0L);
                if (score > best) { best = score; selected = url; }
            }
        }
        return selected;
    }

    private static String mediaUrl(Object node) {
        return mediaUrl(node, 0);
    }

    private static String mediaUrl(Object node, int depth) {
        if (depth > 16) return "";
        if (node instanceof String) {
            String value = (String) node;
            if (value.startsWith("//")) value = "https:" + value;
            if (value.startsWith("http://")) value = "https://" + value.substring(7);
            try {
                URI uri = new URI(value);
                value = uri.toASCIIString();
                if (NetworkPolicy.isKuaishouMediaUrl(value)) return value;
            } catch (Exception ignored) { }
        } else if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            for (String key : new String[]{"url", "urls", "urlList", "url_list"}) {
                String value = mediaUrl(object.opt(key), depth + 1);
                if (!value.isEmpty()) return value;
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length() && i < 1000; i++) {
                String value = mediaUrl(array.opt(i), depth + 1);
                if (!value.isEmpty()) return value;
            }
        }
        return "";
    }

    private static URI pageUri(String value) throws IOException {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getRawFragment() != null) throw new IOException("快手链接格式不受支持");
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.equals("v.kuaishou.com") || host.equals("www.kuaishou.com") || host.equals("kuaishou.com")
                    || host.equals("c.kuaishou.com") || host.equals("www.gifshow.com")
                    || host.equals("v.m.chenzhongtech.com") || host.equals("v.chenzhongtech.com")) return uri;
            throw new IOException("快手链接跳转到不支持的网站");
        } catch (java.net.URISyntaxException e) { throw new IOException("快手链接格式不正确", e); }
    }

    private Page fetch(URI initial) throws IOException {
        URI uri = initial;
        for (int redirect = 0; redirect <= 5; redirect++) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("解析已取消");
            uri = pageUri(uri.toString());
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "text/html,application/json");
            connection.setRequestProperty("Accept-Encoding", "gzip");
            for (Map.Entry<String, List<String>> header : cookies.get(uri, Collections.emptyMap()).entrySet()) {
                connection.setRequestProperty(header.getKey(), String.join("; ", header.getValue()));
            }
            try {
                int code = connection.getResponseCode();
                cookies.put(uri, connection.getHeaderFields());
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("快手链接重定向缺少目标地址");
                    try { uri = pageUri(uri.resolve(location).toString()); }
                    catch (IllegalArgumentException e) { throw new IOException("快手重定向地址无效", e); }
                    continue;
                }
                if (code != 200) throw new IOException("快手页面请求失败（HTTP " + code + "）");
                if (connection.getContentLengthLong() > MAX_BYTES) throw new IOException("快手页面内容过大");
                InputStream raw = connection.getInputStream();
                try (InputStream in = "gzip".equalsIgnoreCase(connection.getContentEncoding()) ? new GZIPInputStream(raw) : raw;
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new IOException("解析已取消");
                        if (out.size() + count > MAX_BYTES) throw new IOException("快手页面内容过大");
                        out.write(buffer, 0, count);
                    }
                    return new Page(uri, new String(out.toByteArray(), StandardCharsets.UTF_8));
                }
            } finally { connection.disconnect(); }
        }
        throw new IOException("快手链接重定向次数过多");
    }

    private static final class Budget { int nodes; }
    private static final class Page {
        final URI uri;
        final String html;
        Page(URI uri, String html) { this.uri = uri; this.html = html; }
    }
}
