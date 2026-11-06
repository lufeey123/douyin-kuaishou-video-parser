package com.example.videoparser.parser;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** A small, explicit allowlist. Unknown hosts fail closed rather than accepting arbitrary URLs. */
public final class UrlPolicy {
    private UrlPolicy() { }

    public static URI page(String value) throws IOException {
        URI uri = https(value);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!(host.equals("v.douyin.com") || host.equals("www.douyin.com")
                || host.equals("douyin.com") || host.equals("www.iesdouyin.com")
                || host.equals("iesdouyin.com") || host.equals("m.douyin.com"))) {
            throw new IOException("只支持抖音分享链接");
        }
        return uri;
    }

    /** Used again before preview/download; returned URL is always HTTPS. */
    public static String media(String value) throws IOException {
        if (value == null || value.isEmpty()) throw new IOException("播放地址为空");
        if (value.startsWith("//")) value = "https:" + value;
        if (value.startsWith("http://")) value = "https://" + value.substring(7);
        URI uri = https(value);
        if (com.example.videoparser.NetworkPolicy.isDouyinMediaUrl(uri.toASCIIString())) {
            return uri.toASCIIString();
        }
        throw new IOException("视频或图片地址不在支持的域名范围内");
    }

    private static URI https(String value) throws IOException {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getRawFragment() != null) throw new IOException("链接格式不受支持");
            return uri;
        } catch (URISyntaxException | NullPointerException e) {
            throw new IOException("链接格式不正确", e);
        }
    }
}
