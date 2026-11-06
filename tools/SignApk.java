import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/** Local debug signing only; never use this public debug password for releases. */
public final class SignApk {
    public static void main(String[] args) throws Exception {
        if (args.length == 3) {
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (FileInputStream in = new FileInputStream(args[0])) {
                store.load(in, "android".toCharArray());
            }
            ApkSigner.SignerConfig config = new ApkSigner.SignerConfig.Builder(
                    "androiddebugkey", (PrivateKey) store.getKey("androiddebugkey", "android".toCharArray()),
                    Collections.singletonList((X509Certificate) store.getCertificate("androiddebugkey"))).build();
            new ApkSigner.Builder(Collections.singletonList(config))
                    .setInputApk(new File(args[1])).setOutputApk(new File(args[2]))
                    .setMinSdkVersion(29).setV1SigningEnabled(false)
                    .setV2SigningEnabled(true).setV3SigningEnabled(true)
                    .setV4SigningEnabled(false).build().sign();
        }
        String apk = args.length == 3 ? args[2] : args[0];
        ApkVerifier.Result result = new ApkVerifier.Builder(new File(apk))
                .setMinCheckedPlatformVersion(29).build().verify();
        if (!result.isVerified()) throw new IllegalStateException("APK signature verification failed: " + result.getErrors());
        System.out.println("APK signature verified: v2=" + result.isVerifiedUsingV2Scheme()
                + ", v3=" + result.isVerifiedUsingV3Scheme());
    }
}
