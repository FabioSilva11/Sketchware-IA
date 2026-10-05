package pro.sketchware.utility;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.ResponseInfo;
import com.google.android.gms.ads.VideoOptions;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import pro.sketchware.BuildConfig;
import pro.sketchware.R;

/** Centralizes consent, lifecycle-safe loading and non-sensitive ad funnel telemetry. */
public final class AdManager {
    public static final String NATIVE_AD_UNIT_ID = "ca-app-pub-6598765502914364/1267873579";
    private static final String TAG = "AdManager";
    private static final String TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111";
    private static final String TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110";
    private static final long RETRY_BASE_MS = 15_000L;
    private static final long RETRY_MAX_MS = 5 * 60_000L;
    private static final AtomicBoolean consentStarted = new AtomicBoolean(false);
    private static final AtomicBoolean adsInitialized = new AtomicBoolean(false);
    private static final AtomicBoolean nativeAdLoading = new AtomicBoolean(false);
    private static final List<Runnable> pendingAfterConsent = new ArrayList<>();
    private static final List<NativeAdLoadCallback> pendingNativeCallbacks = new ArrayList<>();
    private static final Map<ViewGroup, BannerSlot> bannerSlots =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<ViewGroup, PendingBannerLoad> pendingBannerLoads =
            Collections.synchronizedMap(new WeakHashMap<>());
    /** Per ad unit back-off after failed loads, so low fill doesn't turn every screen visit into a wasted request. */
    private static final Map<String, Backoff> bannerBackoff = Collections.synchronizedMap(new java.util.HashMap<>());
    private static volatile Context appContext;
    private static volatile ConsentInformation consentInformation;
    @Nullable private static NativeAd cachedNativeAd;
    @Nullable private static String cachedNativeAdUnitId;

    private AdManager() { }

    public static void initialize(@NonNull Application application) {
        appContext = application.getApplicationContext();
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle state) { }
            @Override public void onActivityStarted(@NonNull Activity activity) { }
            @Override public void onActivityResumed(@NonNull Activity activity) { requestConsent(activity); setBannersPaused(activity, false); }
            @Override public void onActivityPaused(@NonNull Activity activity) { setBannersPaused(activity, true); }
            @Override public void onActivityStopped(@NonNull Activity activity) { }
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle state) { }
            @Override public void onActivityDestroyed(@NonNull Activity activity) { destroyBannersFor(activity); }
        });
    }

    private static void requestConsent(@NonNull Activity activity) {
        if (!isActivityUsable(activity) || !consentStarted.compareAndSet(false, true)) return;
        consentInformation = UserMessagingPlatform.getConsentInformation(activity);
        ConsentRequestParameters parameters = new ConsentRequestParameters.Builder().build();
        consentInformation.requestConsentInfoUpdate(activity, parameters, () -> {
            requestAdsIfAllowed();
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, formError -> requestAdsIfAllowed());
        }, error -> {
            log("consent_update_failed", "consent", "application", error.getErrorCode(), error.getMessage(), 0, 0, null);
            requestAdsIfAllowed(); // UMP may retain a valid prior-session decision.
        });
    }

    private static void requestAdsIfAllowed() {
        ConsentInformation info = consentInformation;
        if (info == null || !info.canRequestAds()) return;
        initializeMobileAds();
    }

    private static void initializeMobileAds() {
        if (appContext == null || !adsInitialized.compareAndSet(false, true)) return;
        if (BuildConfig.DEBUG) MobileAds.setRequestConfiguration(new RequestConfiguration.Builder().build());
        MobileAds.initialize(appContext, status -> flushPendingLoads());
    }

    private static void runWhenAdsReady(@NonNull Runnable operation) {
        if (adsInitialized.get()) { operation.run(); return; }
        synchronized (pendingAfterConsent) { pendingAfterConsent.add(operation); }
    }

    private static void flushPendingLoads() {
        List<Runnable> work;
        synchronized (pendingAfterConsent) { work = new ArrayList<>(pendingAfterConsent); pendingAfterConsent.clear(); }
        for (Runnable runnable : work) runnable.run();
    }

    public static boolean isPrivacyOptionsRequired() {
        ConsentInformation info = consentInformation;
        return info != null && info.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    public static void showPrivacyOptions(@NonNull Activity activity) {
        if (!isActivityUsable(activity)) return;
        UserMessagingPlatform.showPrivacyOptionsForm(activity, error -> {
            if (error != null) log("consent_privacy_form_failed", "consent", screen(activity), error.getErrorCode(), error.getMessage(), 0, 0, null);
        });
    }

    public static void openAdInspector(@NonNull Activity activity) {
        if (!BuildConfig.DEBUG || !isActivityUsable(activity)) return;
        MobileAds.openAdInspector(activity, error -> {
            if (error != null) log("ad_inspector_failed", "debug", screen(activity), error.getCode(), error.getMessage(), 0, 0, null);
        });
    }

    public static void loadBanner(@NonNull Activity activity, @NonNull ViewGroup container, @NonNull String adUnitId) {
        loadBannerInternal(activity, container, adUnitId, false);
    }

    public static void loadBannerFixed(@NonNull Activity activity, @NonNull ViewGroup container, @NonNull String adUnitId) {
        loadBannerInternal(activity, container, adUnitId, true);
    }

    private static void loadBannerInternal(@NonNull Activity activity, @NonNull ViewGroup container,
                                           @NonNull String requestedAdUnitId, boolean fixedSize) {
        if (!isActivityUsable(activity)) { log("ad_activity_destroyed", "banner", requestedAdUnitId, 0, null, 0, 0, null); return; }
        runWhenAdsReady(() -> {
            if (!isActivityUsable(activity)) {
                log("ad_activity_destroyed", "banner", requestedAdUnitId, 0, null, 0, 0, null); return;
            }
            // Request only for a container that is on screen and measured: an adaptive banner sized before
            // layout used the full display width and was cut by the container's padding, and hidden
            // containers produced requests whose ads were never visible.
            if (!isContainerReady(container)) {
                waitForBannerContainer(activity, container, requestedAdUnitId, fixedSize);
                return;
            }
            BannerSlot old = bannerSlots.get(container);
            if (old != null && (old.loading || old.loaded)) {
                log("ad_duplicate_request", "banner", requestedAdUnitId, 0, null, 0, 0, null); return;
            }
            Backoff backoff = bannerBackoff.get(requestedAdUnitId);
            if (backoff != null && SystemClock.elapsedRealtime() < backoff.nextAllowedAt) {
                log("ad_request_skipped", "banner", requestedAdUnitId, 0, "backoff", 0, 0, null); return;
            }
            if (old != null) old.destroy();
            purgeDetachedSlots(activity);
            AdView adView = new AdView(activity);
            adView.setAdUnitId(resolveAdUnitId("banner", requestedAdUnitId));
            adView.setAdSize(fixedSize ? AdSize.BANNER : getAdaptiveBannerAdSize(activity, container));
            BannerSlot slot = new BannerSlot(activity, container, adView, requestedAdUnitId);
            bannerSlots.put(container, slot);
            container.removeAllViews();
            container.addView(adView);
            slot.load();
        });
    }

    private static boolean isContainerReady(@NonNull ViewGroup container) {
        return container.isAttachedToWindow() && container.isShown() && container.getWidth() > 0;
    }

    private static void waitForBannerContainer(@NonNull Activity activity, @NonNull ViewGroup container,
                                               @NonNull String adUnitId, boolean fixedSize) {
        synchronized (pendingBannerLoads) {
            if (pendingBannerLoads.containsKey(container)) return;
            PendingBannerLoad pending = new PendingBannerLoad(activity, container, adUnitId, fixedSize);
            pendingBannerLoads.put(container, pending);
            pending.start();
        }
    }

    @NonNull private static AdSize getAdaptiveBannerAdSize(@NonNull Activity activity, @NonNull ViewGroup container) {
        int pixels = container.getWidth() - container.getPaddingLeft() - container.getPaddingRight();
        if (pixels <= 0) pixels = activity.getResources().getDisplayMetrics().widthPixels;
        int widthDp = Math.max(1, (int) (pixels / activity.getResources().getDisplayMetrics().density));
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp);
    }

    private static void setBannersPaused(@NonNull Activity activity, boolean paused) {
        synchronized (bannerSlots) {
            for (BannerSlot slot : bannerSlots.values()) {
                if (slot.activity.get() == activity) slot.setPaused(paused);
            }
        }
    }

    /** Destroys banners whose container left the window, e.g. a fragment view that was recreated. */
    private static void purgeDetachedSlots(@NonNull Activity activity) {
        synchronized (bannerSlots) {
            bannerSlots.entrySet().removeIf(entry -> {
                BannerSlot slot = entry.getValue();
                if (slot.activity.get() == activity && !entry.getKey().isAttachedToWindow()) {
                    slot.destroy();
                    return true;
                }
                return false;
            });
        }
    }

    private static final class Backoff {
        int failures;
        long nextAllowedAt;
    }

    private static void onBannerResult(@NonNull String adUnitId, boolean success) {
        synchronized (bannerBackoff) {
            if (success) {
                bannerBackoff.remove(adUnitId);
                return;
            }
            Backoff backoff = bannerBackoff.get(adUnitId);
            if (backoff == null) {
                backoff = new Backoff();
                bannerBackoff.put(adUnitId, backoff);
            }
            backoff.failures++;
            long delay = Math.min(RETRY_MAX_MS, RETRY_BASE_MS << Math.min(backoff.failures - 1, 5));
            backoff.nextAllowedAt = SystemClock.elapsedRealtime() + delay;
        }
    }

    public interface NativeAdLoadCallback {
        void onNativeAdLoaded(@NonNull NativeAd nativeAd);
        default void onNativeAdFailedToLoad(@NonNull LoadAdError error) { }
    }

    public static void preloadNativeAd(@NonNull Context context) { preloadNativeAd(context, NATIVE_AD_UNIT_ID, null); }

    public static void preloadNativeAd(@NonNull Context context, @NonNull String requestedAdUnitId,
                                       @Nullable NativeAdLoadCallback callback) {
        runWhenAdsReady(() -> loadNativeOnce(context.getApplicationContext(), requestedAdUnitId, callback));
    }

    private static void loadNativeOnce(@NonNull Context context, @NonNull String requestedAdUnitId,
                                       @Nullable NativeAdLoadCallback callback) {
        synchronized (AdManager.class) {
            if (cachedNativeAd != null && requestedAdUnitId.equals(cachedNativeAdUnitId)) {
                log("ad_duplicate_request", "native", requestedAdUnitId, 0, null, 0, 0, null);
                if (callback != null) callback.onNativeAdLoaded(cachedNativeAd);
                return;
            }
        }
        if (!nativeAdLoading.compareAndSet(false, true)) {
            synchronized (pendingNativeCallbacks) { if (callback != null) pendingNativeCallbacks.add(callback); }
            log("ad_duplicate_request", "native", requestedAdUnitId, 0, null, 0, 0, null);
            return;
        }
        long started = SystemClock.elapsedRealtime();
        log("ad_request", "native", requestedAdUnitId, 0, null, 0, 0, null);
        AdLoader loader = new AdLoader.Builder(context, resolveAdUnitId("native", requestedAdUnitId))
                .forNativeAd(ad -> {
                    synchronized (AdManager.class) { discardCachedNativeAd(); cachedNativeAd = ad; cachedNativeAdUnitId = requestedAdUnitId; }
                    nativeAdLoading.set(false);
                    log("ad_loaded", "native", requestedAdUnitId, 0, null, SystemClock.elapsedRealtime() - started, 0, ad.getResponseInfo());
                    notifyNativeLoaded(callback, ad);
                })
                .withAdListener(new AdListener() {
                    @Override public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        nativeAdLoading.set(false);
                        logLoadError("native", requestedAdUnitId, error, started);
                        notifyNativeFailed(callback, error);
                    }
                })
                .withNativeAdOptions(new NativeAdOptions.Builder().setVideoOptions(new VideoOptions.Builder().setStartMuted(true).build()).build())
                .build();
        loader.loadAd(new AdRequest.Builder().build());
    }

    private static void notifyNativeLoaded(@Nullable NativeAdLoadCallback first, @NonNull NativeAd ad) {
        if (first != null) first.onNativeAdLoaded(ad);
        List<NativeAdLoadCallback> callbacks;
        synchronized (pendingNativeCallbacks) { callbacks = new ArrayList<>(pendingNativeCallbacks); pendingNativeCallbacks.clear(); }
        for (NativeAdLoadCallback callback : callbacks) callback.onNativeAdLoaded(ad);
    }

    private static void notifyNativeFailed(@Nullable NativeAdLoadCallback first, @NonNull LoadAdError error) {
        if (first != null) first.onNativeAdFailedToLoad(error);
        List<NativeAdLoadCallback> callbacks;
        synchronized (pendingNativeCallbacks) { callbacks = new ArrayList<>(pendingNativeCallbacks); pendingNativeCallbacks.clear(); }
        for (NativeAdLoadCallback callback : callbacks) callback.onNativeAdFailedToLoad(error);
    }

    /**
     * Keeps a floating bar (FAB, import/delete cards) above the banner below it, following the banner's real height:
     * a floating control drawn over an ad invites accidental clicks, which AdMob policy forbids.
     */
    public static void keepAboveBanner(@NonNull View floating, @NonNull View adContainer) {
        adContainer.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> floating.setTranslationY(-(b - t)));
        floating.setTranslationY(-adContainer.getHeight());
    }

    @Nullable public static synchronized NativeAd getCachedNativeAd() { return cachedNativeAd; }

    /** Removes the given ad from the shared cache so only its new owner shows (and destroys) it. */
    public static synchronized void releaseFromCache(@NonNull NativeAd ad) {
        if (cachedNativeAd == ad) { cachedNativeAd = null; cachedNativeAdUnitId = null; }
    }

    public static void consumeCachedNativeAd(@NonNull NativeAdContainerBinder binder, @Nullable NativeAdLoadCallback fallback) {
        NativeAd cached;
        synchronized (AdManager.class) { cached = cachedNativeAd; cachedNativeAd = null; cachedNativeAdUnitId = null; }
        if (cached != null) { binder.bind(cached); return; }
        Context context = binder.getContext();
        if (context == null) return;
        preloadNativeAd(context, NATIVE_AD_UNIT_ID, new NativeAdLoadCallback() {
            @Override public void onNativeAdLoaded(@NonNull NativeAd ad) {
                synchronized (AdManager.class) { if (cachedNativeAd == ad) { cachedNativeAd = null; cachedNativeAdUnitId = null; } }
                binder.bind(ad);
                if (fallback != null) fallback.onNativeAdLoaded(ad);
            }
            @Override public void onNativeAdFailedToLoad(@NonNull LoadAdError error) { if (fallback != null) fallback.onNativeAdFailedToLoad(error); }
        });
    }

    public static void populateNativeAdView(@NonNull NativeAd nativeAd, @NonNull NativeAdView adView) {
        if (nativeAd.getHeadline() == null) { adView.setVisibility(View.GONE); log("ad_discarded", "native", "native_list", 0, "missing_headline", 0, 0, nativeAd.getResponseInfo()); return; }
        adView.setHeadlineView(adView.findViewById(R.id.native_ad_headline)); adView.setBodyView(adView.findViewById(R.id.native_ad_body));
        adView.setCallToActionView(adView.findViewById(R.id.native_ad_call_to_action)); adView.setIconView(adView.findViewById(R.id.native_ad_icon));
        adView.setStarRatingView(adView.findViewById(R.id.native_ad_stars)); adView.setMediaView(adView.findViewById(R.id.native_ad_media));
        adView.setAdvertiserView(adView.findViewById(R.id.native_ad_advertiser)); adView.setStoreView(adView.findViewById(R.id.native_ad_store)); adView.setPriceView(adView.findViewById(R.id.native_ad_price));
        setText(adView.getHeadlineView(), nativeAd.getHeadline()); setText(adView.getBodyView(), nativeAd.getBody()); setText(adView.getCallToActionView(), nativeAd.getCallToAction());
        if (adView.getIconView() instanceof ImageView) ((ImageView) adView.getIconView()).setImageDrawable(nativeAd.getIcon() == null ? null : nativeAd.getIcon().getDrawable());
        setVisible(adView.getIconView(), nativeAd.getIcon() != null); setVisible(adView.getMediaView(), nativeAd.getMediaContent() != null);
        if (adView.getMediaView() != null && nativeAd.getMediaContent() != null) adView.getMediaView().setMediaContent(nativeAd.getMediaContent());
        if (adView.getStarRatingView() instanceof RatingBar && nativeAd.getStarRating() != null) ((RatingBar) adView.getStarRatingView()).setRating(nativeAd.getStarRating().floatValue());
        setVisible(adView.getStarRatingView(), nativeAd.getStarRating() != null); setText(adView.getAdvertiserView(), nativeAd.getAdvertiser()); setText(adView.getStoreView(), nativeAd.getStore()); setText(adView.getPriceView(), nativeAd.getPrice());
        adView.setNativeAd(nativeAd); adView.setVisibility(View.VISIBLE);
        adView.post(() -> log(adView.isShown() ? "ad_show_attempt" : "ad_view_not_visible", "native", "native_list", 0, null, 0, 0, nativeAd.getResponseInfo()));
    }

    private static void setText(@Nullable View view, @Nullable String text) { if (view instanceof TextView) ((TextView) view).setText(text); setVisible(view, text != null); }
    private static void setVisible(@Nullable View view, boolean visible) { if (view != null) view.setVisibility(visible ? View.VISIBLE : View.GONE); }
    private static synchronized void discardCachedNativeAd() { if (cachedNativeAd != null) { cachedNativeAd.destroy(); cachedNativeAd = null; cachedNativeAdUnitId = null; } }

    public interface NativeAdContainerBinder { void bind(@NonNull NativeAd nativeAd); @Nullable Context getContext(); }

    private static final class BannerSlot {
        final WeakReference<Activity> activity; final WeakReference<ViewGroup> container; final AdView adView; final String placement; final long started = SystemClock.elapsedRealtime();
        boolean loading = true, loaded, destroyed, paused;
        BannerSlot(Activity activity, ViewGroup container, AdView adView, String placement) { this.activity = new WeakReference<>(activity); this.container = new WeakReference<>(container); this.adView = adView; this.placement = placement; }
        void load() {
            log("ad_request", "banner", placement, 0, null, 0, 0, null);
            adView.setAdListener(new AdListener() {
                @Override public void onAdLoaded() { loading = false; loaded = true; onBannerResult(placement, true); log("ad_loaded", "banner", placement, 0, null, SystemClock.elapsedRealtime() - started, 0, adView.getResponseInfo()); adView.post(() -> inspectBanner(BannerSlot.this)); }
                @Override public void onAdFailedToLoad(@NonNull LoadAdError error) { loading = false; onBannerResult(placement, false); logLoadError("banner", placement, error, started); }
                @Override public void onAdImpression() { log("ad_impression", "banner", placement, 0, null, SystemClock.elapsedRealtime() - started, 0, adView.getResponseInfo()); }
                @Override public void onAdClicked() { log("ad_clicked", "banner", placement, 0, null, 0, 0, adView.getResponseInfo()); }
            });
            adView.loadAd(new AdRequest.Builder().build());
        }
        /** Paused banners stop their automatic refresh, so screens in the background don't keep requesting ads. */
        void setPaused(boolean pause) {
            if (destroyed || paused == pause) return;
            paused = pause;
            if (pause) adView.pause(); else adView.resume();
        }
        void destroy() { if (!destroyed) { destroyed = true; adView.destroy(); } }
    }

    /** Waits until a banner container is attached, visible and measured, then loads the banner once. */
    private static final class PendingBannerLoad implements View.OnAttachStateChangeListener, android.view.ViewTreeObserver.OnGlobalLayoutListener {
        final WeakReference<Activity> activity;
        final WeakReference<ViewGroup> container;
        final String adUnitId;
        final boolean fixedSize;
        private boolean done;

        PendingBannerLoad(@NonNull Activity activity, @NonNull ViewGroup container,
                          @NonNull String adUnitId, boolean fixedSize) {
            this.activity = new WeakReference<>(activity);
            this.container = new WeakReference<>(container);
            this.adUnitId = adUnitId;
            this.fixedSize = fixedSize;
        }

        void start() {
            ViewGroup target = container.get();
            if (target == null) return;
            target.addOnAttachStateChangeListener(this);
            if (target.isAttachedToWindow()) target.getViewTreeObserver().addOnGlobalLayoutListener(this);
        }

        @Override public void onViewAttachedToWindow(@NonNull View view) {
            view.getViewTreeObserver().addOnGlobalLayoutListener(this);
            onGlobalLayout();
        }

        @Override public void onViewDetachedFromWindow(@NonNull View view) {
            view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        }

        @Override public void onGlobalLayout() {
            ViewGroup target = container.get();
            Activity owner = activity.get();
            if (done || target == null) return;
            if (!isActivityUsable(owner)) { finish(target); return; }
            if (!isContainerReady(target)) return;
            finish(target);
            // Post so the request doesn't run inside a layout pass.
            target.post(() -> { if (isActivityUsable(owner)) loadBannerInternal(owner, target, adUnitId, fixedSize); });
        }

        private void finish(@NonNull ViewGroup target) {
            done = true;
            target.removeOnAttachStateChangeListener(this);
            target.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            synchronized (pendingBannerLoads) { pendingBannerLoads.remove(target); }
        }
    }

    private static void inspectBanner(@NonNull BannerSlot slot) {
        ViewGroup container = slot.container.get(); Activity activity = slot.activity.get();
        if (container == null || activity == null || !isActivityUsable(activity)) { log("ad_activity_destroyed", "banner", slot.placement, 0, null, 0, 0, slot.adView.getResponseInfo()); return; }
        boolean visible = container.isShown() && slot.adView.isShown() && container.getWidth() > 0 && container.getHeight() > 0 && slot.adView.getAlpha() > 0f;
        log(visible ? "ad_show_attempt" : "ad_view_not_visible", "banner", slot.placement, 0, visible ? null : "container_not_visible", 0, 0, slot.adView.getResponseInfo());
    }
    private static void destroyBannersFor(@NonNull Activity activity) { synchronized (bannerSlots) { bannerSlots.entrySet().removeIf(entry -> { BannerSlot slot = entry.getValue(); if (slot.activity.get() == activity) { slot.destroy(); return true; } return false; }); } }
    private static boolean isActivityUsable(@Nullable Activity activity) { return activity != null && !activity.isFinishing() && !activity.isDestroyed(); }
    private static String resolveAdUnitId(String format, String production) { if (!BuildConfig.DEBUG) return production; return "native".equals(format) ? TEST_NATIVE_AD_UNIT_ID : TEST_BANNER_AD_UNIT_ID; }
    private static String screen(@NonNull Activity activity) { return activity.getClass().getSimpleName(); }
    private static void logLoadError(String format, String placement, LoadAdError error, long started) { log("ad_load_failed", format, placement, error.getCode(), error.getMessage(), SystemClock.elapsedRealtime() - started, 0, error.getResponseInfo()); }
    private static void log(String event, String format, String placement, int errorCode, @Nullable String message, long loadDuration, long timeUntilShow, @Nullable ResponseInfo responseInfo) {
        Log.i(TAG, event + " format=" + format + " placement=" + placement + " code=" + errorCode + " message=" + message + " response=" + (responseInfo == null ? "" : responseInfo.getResponseId()));
        if (appContext == null) return;
        Bundle params = new Bundle(); params.putString("ad_format", format); params.putString("placement_name", placement); params.putString("screen_name", "sdk_managed"); params.putLong("load_duration_ms", loadDuration); params.putLong("time_until_show_ms", timeUntilShow); params.putString("build_type", BuildConfig.DEBUG ? "debug" : "release");
        if (errorCode != 0) params.putLong("error_code", errorCode); if (responseInfo != null && responseInfo.getResponseId() != null) params.putString("response_id", responseInfo.getResponseId());
        FirebaseAnalytics.getInstance(appContext).logEvent(event, params);
    }
}
