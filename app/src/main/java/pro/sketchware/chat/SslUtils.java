package pro.sketchware.chat;

import okhttp3.OkHttpClient;

/**
 * Hardened TLS configuration for OkHttp clients.
 *
 * Replaces the previous trust-all TrustManager/HostnameVerifier: API keys and
 * workspace traffic now fail closed against invalid certificates instead of
 * being sent through any MITM proxy that presents a self-signed cert.
 *
 * The system trust anchors already cover user-installed CAs on devices where
 * the user deliberately configured them (the app's network security config
 * keeps {@code <certificates src="user" />} for debugging proxies like
 * Charles/Fiddler when explicitly installed by the device owner).
 */
public final class SslUtils {
    private SslUtils() {
    }

    /** Returns a builder with default (validated) TLS from the platform trust anchors. */
    public static OkHttpClient.Builder hardenedClientBuilder() {
        return new OkHttpClient.Builder();
    }

    /**
     * Compatibility alias for previous call sites of {@code relaxedClientBuilder}.
     * TLS is now validated; the name remains so call sites keep compiling.
     */
    @Deprecated
    public static OkHttpClient.Builder relaxedClientBuilder() {
        return hardenedClientBuilder();
    }
}
