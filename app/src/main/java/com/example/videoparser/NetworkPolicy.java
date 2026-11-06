package com.example.videoparser;

import java.net.URI;
import java.util.Locale;

/** A fixed allowlist, shared by image previews and DownloadManager requests. */
public final class NetworkPolicy {
    private static final String[] DOUYIN_DOMAINS = {
        "douyin.com", "iesdouyin.com", "amemv.com", "douyinvod.com",
        "douyinpic.com", "douyinstatic.com", "byteimg.com", "ibytedtos.com",
        "bytecdn.cn", "bytecdn.com", "pstatp.com", "snssdk.com",
        "ixigua.com", "vcloud-douyin.com", "bytegoofy.com"
    };
    private static final String[] KUAISHOU_DOMAINS = {
        "kuaishou.com", "gifshow.com", "kwimgs.com", "yximgs.com",
        "kwai.net", "kwaicdn.com", "ksapisrv.com", "ksvideo.com", "kscdn.com"
    };

    private NetworkPolicy() {}

    public static boolean isMediaUrl(String value) {
        return isDouyinMediaUrl(value) || isKuaishouMediaUrl(value);
    }

    public static boolean isDouyinMediaUrl(String value) {
        return matches(value, DOUYIN_DOMAINS);
    }

    public static boolean isKuaishouMediaUrl(String value) {
        return matches(value, KUAISHOU_DOMAINS);
    }

    private static boolean matches(String value, String[] domains) {
        if (value == null || value.length() > 16384) return false;
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            for (String domain : domains) {
                if (host.equals(domain) || host.endsWith("." + domain)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }
}
