package com.example.videoparser.parser;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Routes a supported share URL to its own platform parser. */
public final class VideoParser {
    public static final String USER_AGENT = DouyinParser.USER_AGENT;
    public static final int MAX_BATCH_SIZE = 20;
    public static final int MAX_INPUT_LENGTH = 64000;
    private static final Pattern LINKS = Pattern.compile("https?://[A-Za-z0-9.-]+(?::[0-9]+)?[^\\s<>\\\"“”，。！？、；）【】]*", Pattern.CASE_INSENSITIVE);
    private static final String[] KUAISHOU_HOSTS = {
        "v.kuaishou.com", "www.kuaishou.com", "kuaishou.com", "c.kuaishou.com",
        "www.gifshow.com", "v.m.chenzhongtech.com", "v.chenzhongtech.com"
    };

    public VideoInfo parse(String input) throws Exception {
        URI link = extractLink(input);
        if (isKuaishouHost(link.getHost())) return new KuaishouParser().parse(link.toASCIIString());
        return new DouyinParser().parse(link.toASCIIString());
    }

    static URI extractLink(String input) throws IOException {
        return URI.create(extractLinks(input, MAX_BATCH_SIZE).get(0));
    }

    /** Extract supported links in input order, with exact normalized URL deduplication. */
    public static List<String> extractLinks(String input, int maxCount) throws IOException {
        if (maxCount < 1 || maxCount > MAX_BATCH_SIZE) throw new IllegalArgumentException("批量上限必须为 1 至 20");
        if (input == null || input.trim().isEmpty()) throw new IOException("请粘贴抖音或快手分享内容");
        if (input.length() > MAX_INPUT_LENGTH) throw new IOException("分享文本过长，请分批解析");
        LinkedHashSet<String> links = new LinkedHashSet<>();
        Matcher matches = LINKS.matcher(input);
        while (matches.find()) {
            String value = matches.group();
            // Never trim a signed query's legal punctuation as if it were prose.
            if (!value.contains("?")) value = value.replaceAll("[，。！？、；：）)\\]}.,;!]+$", "");
            URI normalized;
            try {
                URI uri = new URI(value);
                if (uri.getHost() == null || uri.getRawUserInfo() != null
                        || uri.getRawFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)) continue;
                // Older share texts sometimes contain HTTP. Never transmit in cleartext.
                if ("http".equalsIgnoreCase(uri.getScheme())) uri = new URI("https" + value.substring(value.indexOf(':')));
                if (!isKuaishouHost(uri.getHost())) UrlPolicy.page(uri.toString());
                String path = uri.getRawPath() == null ? "" : uri.getRawPath();
                String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
                normalized = new URI("https://" + uri.getHost().toLowerCase(Locale.ROOT) + path + query);
            } catch (Exception ignored) { continue; }
            links.add(normalized.toASCIIString());
            if (links.size() > maxCount) throw new IOException("一次最多解析 " + maxCount + " 条不同链接，请分批处理");
        }
        if (links.isEmpty()) throw new IOException("未找到支持的抖音或快手分享链接");
        return new ArrayList<>(links);
    }

    static boolean isKuaishouHost(String host) {
        if (host == null) return false;
        for (String allowed : KUAISHOU_HOSTS) if (allowed.equals(host.toLowerCase(Locale.ROOT))) return true;
        return false;
    }
}
