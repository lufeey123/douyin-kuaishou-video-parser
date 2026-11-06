package com.example.videoparser;

public final class NetworkPolicyTest {
    private static int checks;
    public static void main(String[] args) {
        check("https://v3.douyinvod.com/video/test.mp4?token=abc", true);
        check("https://www.iesdouyin.com/aweme/v1/play/", true);
        check("https://p3.douyinpic.com/image.jpg", true);
        check("http://v3.douyinvod.com/video.mp4", false);
        check("https://douyinvod.com.evil.example/video", false);
        check("https://evildouyinvod.com/video", false);
        check("https://user:secret@v3.douyinvod.com/video", false);
        check("https://v3.douyinvod.com:444/video", false);
        check("https://127.0.0.1/video", false);
        check("https://[::1]/video", false);
        check("file:///storage/emulated/0/video.mp4", false);
        check("https://v3.douyinvod.com\\@evil.example/video", false);
        check(null, false);
        check("https://txmov2.a.kwimgs.com/upic/video.mp4", true);
        check("https://p23.a.yximgs.com/cover.jpg", true);
        check("https://txmov2.a.kwimgs.com.evil.example/video", false);
        check("https://evilkwimgs.com/video", false);
        check("https://user@txmov2.a.kwimgs.com/video", false);
        check("https://txmov2.a.kwimgs.com/video#fragment", false);
        if (NetworkPolicy.isDouyinMediaUrl("https://txmov2.a.kwimgs.com/video")) throw new AssertionError("cross-platform CDN");
        if (NetworkPolicy.isKuaishouMediaUrl("https://v3.douyinvod.com/video")) throw new AssertionError("cross-platform CDN");
        checks += 2;
        System.out.println("NetworkPolicy: " + checks + " checks passed");
    }

    private static void check(String url, boolean expected) {
        checks++;
        if (NetworkPolicy.isMediaUrl(url) != expected) {
            throw new AssertionError("Unexpected URL validation: " + url);
        }
    }
}
