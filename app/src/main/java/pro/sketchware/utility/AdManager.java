package pro.sketchware.utility;

import android.app.Activity;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.google.android.gms.ads.VideoOptions;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

import pro.sketchware.R;

public class AdManager {

    public static final String NATIVE_AD_UNIT_ID = "ca-app-pub-6598765502914364/1267873579";

    public static void loadBanner(Activity activity, ViewGroup container, String adUnitId) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        AdView adView = new AdView(activity);
        adView.setAdSize(getAdaptiveBannerAdSize(activity, container));
        adView.setAdUnitId(adUnitId);

        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

        container.removeAllViews();
        container.addView(adView);
    }

    @NonNull
    private static AdSize getAdaptiveBannerAdSize(@NonNull Activity activity, @Nullable ViewGroup container) {
        int widthPixels;
        if (container != null && container.getWidth() > 0) {
            widthPixels = container.getWidth();
        } else {
            DisplayMetrics outMetrics = activity.getResources().getDisplayMetrics();
            widthPixels = outMetrics.widthPixels;
        }
        int density = (int) (widthPixels / activity.getResources().getDisplayMetrics().density);
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, density);
    }

    public static void loadBannerFixed(Activity activity, ViewGroup container, String adUnitId) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        AdView adView = new AdView(activity);
        adView.setAdSize(AdSize.BANNER);
        adView.setAdUnitId(adUnitId);

        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

        container.removeAllViews();
        container.addView(adView);
    }

    public interface NativeAdLoadCallback {
        void onNativeAdLoaded(@NonNull NativeAd nativeAd);

        default void onNativeAdFailedToLoad(@NonNull LoadAdError error) {
        }
    }

    private static WeakReference<NativeAd> lastLoadedNativeAd;
    private static final AtomicBoolean nativeAdLoading = new AtomicBoolean(false);

    public static void preloadNativeAd(@NonNull Context context) {
        preloadNativeAd(context, NATIVE_AD_UNIT_ID, null);
    }

    public static void preloadNativeAd(@NonNull Context context,
                                       @NonNull String adUnitId,
                                       @Nullable NativeAdLoadCallback callback) {
        if (nativeAdLoading.compareAndSet(false, true)) {
            AdLoader.Builder builder = new AdLoader.Builder(context, adUnitId)
                    .forNativeAd(nativeAd -> {
                        lastLoadedNativeAd = new WeakReference<>(nativeAd);
                        nativeAdLoading.set(false);
                        if (callback != null) {
                            callback.onNativeAdLoaded(nativeAd);
                        }
                    })
                    .withAdListener(new AdListener() {
                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                            nativeAdLoading.set(false);
                            if (callback != null) {
                                callback.onNativeAdFailedToLoad(loadAdError);
                            }
                        }
                    });
            VideoOptions videoOptions = new VideoOptions.Builder()
                    .setStartMuted(true)
                    .build();
            NativeAdOptions adOptions = new NativeAdOptions.Builder()
                    .setVideoOptions(videoOptions)
                    .build();
            builder.withNativeAdOptions(adOptions);
            AdLoader adLoader = builder.build();
            adLoader.loadAd(new AdRequest.Builder().build());
        }
    }

    @Nullable
    public static NativeAd getCachedNativeAd() {
        if (lastLoadedNativeAd != null) {
            return lastLoadedNativeAd.get();
        }
        return null;
    }

    public static void consumeCachedNativeAd(@NonNull NativeAdContainerBinder binder,
                                             @Nullable NativeAdLoadCallback fallbackCallback) {
        NativeAd cached = getCachedNativeAd();
        if (cached != null) {
            binder.bind(cached);
            lastLoadedNativeAd = null;
        } else if (fallbackCallback != null) {
            Context context = binder.getContext();
            if (context != null) {
                preloadNativeAd(context, NATIVE_AD_UNIT_ID, new NativeAdLoadCallback() {
                    @Override
                    public void onNativeAdLoaded(@NonNull NativeAd nativeAd) {
                        binder.bind(nativeAd);
                        lastLoadedNativeAd = null;
                        fallbackCallback.onNativeAdLoaded(nativeAd);
                    }

                    @Override
                    public void onNativeAdFailedToLoad(@NonNull LoadAdError error) {
                        fallbackCallback.onNativeAdFailedToLoad(error);
                    }
                });
            }
        }
    }

    public static void populateNativeAdView(@NonNull NativeAd nativeAd,
                                            @NonNull NativeAdView adView) {
        adView.setHeadlineView(adView.findViewById(R.id.native_ad_headline));
        adView.setBodyView(adView.findViewById(R.id.native_ad_body));
        adView.setCallToActionView(adView.findViewById(R.id.native_ad_call_to_action));
        adView.setIconView(adView.findViewById(R.id.native_ad_icon));
        adView.setStarRatingView(adView.findViewById(R.id.native_ad_stars));
        adView.setMediaView(adView.findViewById(R.id.native_ad_media));
        adView.setAdvertiserView(adView.findViewById(R.id.native_ad_advertiser));
        adView.setStoreView(adView.findViewById(R.id.native_ad_store));
        adView.setPriceView(adView.findViewById(R.id.native_ad_price));

        if (adView.getHeadlineView() instanceof TextView headlineView) {
            if (nativeAd.getHeadline() != null) {
                headlineView.setText(nativeAd.getHeadline());
            }
        }
        if (adView.getBodyView() instanceof TextView bodyView) {
            if (nativeAd.getBody() != null) {
                bodyView.setText(nativeAd.getBody());
                bodyView.setVisibility(View.VISIBLE);
            } else {
                bodyView.setVisibility(View.GONE);
            }
        }
        if (adView.getCallToActionView() instanceof Button ctaView) {
            if (nativeAd.getCallToAction() != null) {
                ctaView.setText(nativeAd.getCallToAction());
                ctaView.setVisibility(View.VISIBLE);
            } else {
                ctaView.setVisibility(View.GONE);
            }
        }
        if (adView.getIconView() instanceof ImageView iconView) {
            if (nativeAd.getIcon() != null) {
                iconView.setImageDrawable(nativeAd.getIcon().getDrawable());
                iconView.setVisibility(View.VISIBLE);
            } else {
                iconView.setVisibility(View.GONE);
            }
        }
        if (adView.getStarRatingView() instanceof RatingBar ratingView) {
            if (nativeAd.getStarRating() != null) {
                ratingView.setRating(nativeAd.getStarRating().floatValue());
                ratingView.setVisibility(View.VISIBLE);
            } else {
                ratingView.setVisibility(View.GONE);
            }
        }
        if (adView.getAdvertiserView() instanceof TextView advView) {
            if (nativeAd.getAdvertiser() != null) {
                advView.setText(nativeAd.getAdvertiser());
                advView.setVisibility(View.VISIBLE);
            } else {
                advView.setVisibility(View.GONE);
            }
        }
        if (adView.getStoreView() instanceof TextView storeView) {
            if (nativeAd.getStore() != null) {
                storeView.setText(nativeAd.getStore());
                storeView.setVisibility(View.VISIBLE);
            } else {
                storeView.setVisibility(View.GONE);
            }
        }
        if (adView.getPriceView() instanceof TextView priceView) {
            if (nativeAd.getPrice() != null) {
                priceView.setText(nativeAd.getPrice());
                priceView.setVisibility(View.VISIBLE);
            } else {
                priceView.setVisibility(View.GONE);
            }
        }

        adView.setNativeAd(nativeAd);
    }

    public interface NativeAdContainerBinder {
        void bind(@NonNull NativeAd nativeAd);

        @Nullable
        Context getContext();
    }
}
