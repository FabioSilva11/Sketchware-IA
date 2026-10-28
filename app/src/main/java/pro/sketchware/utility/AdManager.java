package pro.sketchware.utility;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.unity3d.ads.BannerAd;
import com.unity3d.ads.BannerConfiguration;
import com.unity3d.ads.BannerShowListener;
import com.unity3d.ads.BannerSize;
import com.unity3d.ads.InitializationConfiguration;
import com.unity3d.ads.UnityAds;
import com.unity3d.ads.UnityAdsError;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import pro.sketchware.BuildConfig;

/** Monetization of Sketchware IA itself; independent of ads in user-generated apps. */
public final class AdManager {
    public static final String GAME_ID = "800408466";
    public static final String BANNER_PLACEMENT_ID = "BP_Banner_Android";
    // Reserved for explicit fullscreen/reward flows; the app currently only displays banners.
    public static final String INTERSTITIAL_PLACEMENT_ID = "BP_Interstitial_Android";
    public static final String REWARDED_PLACEMENT_ID = "BP_Rewarded_Android";

    private static final String TAG = "AdManager";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // All SDK state and view operations are confined to the main thread.
    private static final Map<ViewGroup, BannerSlot> BANNERS = new WeakHashMap<>();
    private static final Set<Activity> RESUMED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final AdRequestBackoff INITIALIZATION_BACKOFF = new AdRequestBackoff();
    private static final Runnable RETRY_INITIALIZATION = () -> {
        if (hasActiveBanners()) ensureInitialized();
    };
    private static boolean registered;
    private static boolean initializing;
    private static boolean ready;

    private AdManager() { }

    public static void initialize(@NonNull Application application) {
        if (registered) return;
        registered = true;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle state) { }
            @Override public void onActivityStarted(@NonNull Activity activity) { }
            @Override public void onActivityResumed(@NonNull Activity activity) {
                RESUMED.add(activity);
                ensureInitialized();
                for (BannerSlot slot : new ArrayList<>(BANNERS.values())) {
                    if (slot.activity.get() == activity) slot.tryLoad();
                }
            }
            @Override public void onActivityPaused(@NonNull Activity activity) {
                RESUMED.remove(activity);
                for (BannerSlot slot : new ArrayList<>(BANNERS.values())) {
                    if (slot.activity.get() == activity) MAIN.removeCallbacks(slot.retry);
                }
                if (!hasActiveBanners()) MAIN.removeCallbacks(RETRY_INITIALIZATION);
            }
            @Override public void onActivityStopped(@NonNull Activity activity) { }
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle state) { }
            @Override public void onActivityDestroyed(@NonNull Activity activity) {
                RESUMED.remove(activity);
                for (BannerSlot slot : new ArrayList<>(BANNERS.values())) {
                    if (slot.activity.get() == activity) slot.destroy();
                }
            }
        });
    }

    private static void onMain(Runnable work) {
        if (Looper.myLooper() == Looper.getMainLooper()) work.run();
        else MAIN.post(work);
    }

    private static void ensureInitialized() {
        if (ready || initializing) return;
        if (!INITIALIZATION_BACKOFF.canRequest(SystemClock.elapsedRealtime())) {
            scheduleInitializationRetry();
            return;
        }
        MAIN.removeCallbacks(RETRY_INITIALIZATION);
        initializing = true;
        // Contextual ads only: do not assume consent for profiling.
        applyContextualPrivacy();
        UnityAds.initialize(new InitializationConfiguration.Builder(GAME_ID)
                .withTestMode(BuildConfig.DEBUG).build(), error -> onMain(() -> {
            initializing = false;
            ready = error == null;
            if (!ready) {
                INITIALIZATION_BACKOFF.onFailure(SystemClock.elapsedRealtime());
                log("initialization_failed", error);
                scheduleInitializationRetry();
                return;
            }
            INITIALIZATION_BACKOFF.onSuccess();
            log("initialized", null);
            applyContextualPrivacy();
            for (BannerSlot slot : new ArrayList<>(BANNERS.values())) slot.tryLoad();
        }));
    }

    private static boolean hasActiveBanners() {
        for (BannerSlot slot : new ArrayList<>(BANNERS.values())) {
            if (slot.active()) return true;
        }
        return false;
    }

    private static void scheduleInitializationRetry() {
        MAIN.removeCallbacks(RETRY_INITIALIZATION);
        if (hasActiveBanners()) {
            MAIN.postDelayed(RETRY_INITIALIZATION,
                    INITIALIZATION_BACKOFF.retryDelayMs(SystemClock.elapsedRealtime()));
        }
    }

    private static void applyContextualPrivacy() {
        UnityAds.setUserConsent(false);
        UnityAds.setUserOptOut(true);
        UnityAds.setNonBehavioral(true);
    }

    public static void loadBanner(@NonNull Activity activity, @NonNull ViewGroup container) {
        onMain(() -> {
            if (!usable(activity)) return;
            BannerSlot slot = BANNERS.get(container);
            if (slot == null) {
                slot = new BannerSlot(activity, container);
                BANNERS.put(container, slot);
                slot.watch();
            }
            ensureInitialized();
            slot.tryLoad();
        });
    }

    /** Keeps floating controls clear of the banner's actual height. */
    public static void keepAboveBanner(@NonNull View floating, @NonNull View adContainer) {
        adContainer.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> floating.setTranslationY(-(b - t)));
        floating.setTranslationY(-adContainer.getHeight());
    }

    private static boolean usable(@Nullable Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private static void log(String event, @Nullable UnityAdsError error) {
        Log.i(TAG, "unity_" + event + " placement=" + BANNER_PLACEMENT_ID
                + (error == null ? "" : " code=" + error.getCode() + " message=" + error.getMessage()));
    }

    private static final class BannerSlot implements View.OnAttachStateChangeListener,
            ViewTreeObserver.OnGlobalLayoutListener {
        final WeakReference<Activity> activity;
        final WeakReference<ViewGroup> container;
        final AdRequestBackoff backoff = new AdRequestBackoff();
        final Runnable retry = this::tryLoad;
        BannerAd ad;
        FrameLayout host;
        boolean loading;
        boolean destroyed;
        int generation;

        BannerSlot(Activity activity, ViewGroup container) {
            this.activity = new WeakReference<>(activity);
            this.container = new WeakReference<>(container);
        }

        void watch() {
            ViewGroup target = container.get();
            if (target == null) return;
            target.addOnAttachStateChangeListener(this);
            if (target.isAttachedToWindow()) target.getViewTreeObserver().addOnGlobalLayoutListener(this);
        }

        void tryLoad() {
            ViewGroup target = container.get();
            if (destroyed || target == null || !usable(activity.get()) || loading || ad != null) return;
            if (!active()) return;
            // Unity's standard banner needs 320dp; never crop or scale the clickable creative.
            int available = target.getWidth() - target.getPaddingLeft() - target.getPaddingRight();
            if (available < Math.round(320 * target.getResources().getDisplayMetrics().density)) return;
            if (!ready) {
                ensureInitialized();
                return;
            }
            if (!backoff.canRequest(SystemClock.elapsedRealtime())) {
                scheduleRetry();
                return;
            }
            MAIN.removeCallbacks(retry);
            loading = true;
            int request = ++generation;
            // Avoid modifying the view hierarchy from inside a layout pass.
            target.post(() -> {
                if (!current(request)) return;
                if (!active()) {
                    loading = false;
                    return;
                }
                BannerShowListener listener = new BannerShowListener() {
                    @Override public void onImpression(BannerAd banner) { log("banner_impression", null); }
                    @Override public void onClicked(BannerAd banner) { log("banner_clicked", null); }
                    @Override public void onFailedToShow(BannerAd banner, UnityAdsError error) {
                        onMain(() -> {
                            if (!current(request)) return;
                            clearAd();
                            backoff.onFailure(SystemClock.elapsedRealtime());
                            log("banner_show_failed", error);
                            scheduleRetry();
                        });
                    }
                };
                BannerAd.load(new BannerConfiguration.Builder(BANNER_PLACEMENT_ID,
                        new BannerSize(320, 50), listener).build(), (loaded, error) -> onMain(() -> {
                    if (!current(request) || !target.isAttachedToWindow()) {
                        detach(loaded);
                        return;
                    }
                    loading = false;
                    if (loaded == null || loaded.getView() == null) {
                        backoff.onFailure(SystemClock.elapsedRealtime());
                        log("banner_load_failed", error);
                        scheduleRetry();
                        return;
                    }
                    backoff.onSuccess();
                    ad = loaded;
                    host = new FrameLayout(target.getContext());
                    detach(ad);
                    host.addView(ad.getView(), new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.CENTER_HORIZONTAL));
                    target.addView(host, new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    loaded.setOnAdExpired(expired -> onMain(() -> {
                        if (current(request) && ad == expired) clearAd();
                    }));
                    log("banner_loaded", null);
                }));
            });
        }

        private boolean current(int request) {
            return !destroyed && request == generation && usable(activity.get());
        }

        boolean active() {
            ViewGroup target = container.get();
            Activity owner = activity.get();
            return !destroyed && usable(owner) && RESUMED.contains(owner)
                    && target != null && target.isAttachedToWindow() && target.isShown();
        }

        private void scheduleRetry() {
            MAIN.removeCallbacks(retry);
            if (active()) MAIN.postDelayed(retry, backoff.retryDelayMs(SystemClock.elapsedRealtime()));
        }

        private static void detach(@Nullable BannerAd banner) {
            View view = banner == null ? null : banner.getView();
            if (view != null && view.getParent() instanceof ViewGroup parent) parent.removeView(view);
        }

        private void clearAd() {
            if (ad != null) ad.setOnAdExpired(null);
            detach(ad);
            ad = null;
            if (host != null && host.getParent() instanceof ViewGroup parent) parent.removeView(host);
            host = null;
        }

        void destroy() {
            destroyed = true;
            generation++;
            MAIN.removeCallbacks(retry);
            clearAd();
            ViewGroup target = container.get();
            if (target == null) return;
            target.removeOnAttachStateChangeListener(this);
            target.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            if (BANNERS.get(target) == this) BANNERS.remove(target);
            if (!hasActiveBanners()) MAIN.removeCallbacks(RETRY_INITIALIZATION);
        }

        @Override public void onViewAttachedToWindow(@NonNull View view) {
            view.getViewTreeObserver().addOnGlobalLayoutListener(this);
            tryLoad();
        }
        @Override public void onViewDetachedFromWindow(@NonNull View view) {
            generation++;
            loading = false;
            MAIN.removeCallbacks(retry);
            clearAd();
            view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            if (!hasActiveBanners()) MAIN.removeCallbacks(RETRY_INITIALIZATION);
        }
        @Override public void onGlobalLayout() { tryLoad(); }
    }
}
