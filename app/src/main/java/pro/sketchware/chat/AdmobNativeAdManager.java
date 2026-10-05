package pro.sketchware.chat;


import pro.sketchware.BuildConfig;
import pro.sketchware.utility.AdManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.nativead.NativeAd;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fila de anúncios nativos: carrega exatamente um anúncio por vez.
 *
 * <p>Cada espaço da lista consome um anúncio e o adapter passa a ser o dono
 * daquele objeto durante as reciclagens do RecyclerView. Se o carregamento
 * falhar, apenas uma nova tentativa é agendada com backoff.</p>
 */
public final class AdmobNativeAdManager {

    // Google's official native-ad test unit: zero risk of invalid-traffic
    // suspension while the production unit is not wired to a real inventory.
    private static final long RETRY_DELAY_MS = 30_000L;
    private static final long RETRY_DELAY_MAX_MS = 5 * 60_000L;

    private static volatile AdmobNativeAdManager instance;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final AtomicBoolean retryScheduled = new AtomicBoolean(false);

    private NativeAd loadedAd;
    private long retryDelayMs = RETRY_DELAY_MS;

    public interface Listener {
        void onNativeAdStateChanged();
    }

    private AdmobNativeAdManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static AdmobNativeAdManager getInstance(Context context) {
        if (instance == null) {
            synchronized (AdmobNativeAdManager.class) {
                if (instance == null) {
                    instance = new AdmobNativeAdManager(context);
                }
            }
        }
        return instance;
    }

    /** Inicializa o SDK e carrega o primeiro anúncio. Chamado no Application.onCreate. */
    public void initialize() {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        // O SDK enfileira as requisições até terminar a inicialização.
        requestAdIfNeeded();
    }

    /** True se existe um anúncio pronto para o próximo espaço da lista. */
    public boolean hasAd() {
        return loadedAd != null;
    }

    /**
     * Transfere o anúncio atual para o adapter e inicia o carregamento do
     * próximo. O adapter preserva o objeto por posição para ele não sumir
     * quando o RecyclerView reciclar a linha.
     */
    public NativeAd takeAd() {
        NativeAd ad = loadedAd;
        loadedAd = null;
        if (ad != null) {
            ChatFlowLogger.event("ads", "native_assigned", "unit=chat");
            requestAdIfNeeded();
        }
        return ad;
    }

    /** Pede um anúncio apenas se não houver um carregado ou carregando. */
    public void requestAdIfNeeded() {
        if (!initialized.get() || loading.get() || loadedAd != null
                || retryScheduled.get()) {
            return;
        }
        loading.set(true);
        ChatFlowLogger.event("ads", "native_request_started",
                "unit=chat, debug=" + BuildConfig.DEBUG);
        // Sketchware's AdManager owns consent, the ad unit and test ids in debug builds
        AdManager.preloadNativeAd(appContext, AdManager.NATIVE_AD_UNIT_ID, new AdManager.NativeAdLoadCallback() {
            @Override
            public void onNativeAdLoaded(@NonNull NativeAd nativeAd) {
                mainHandler.post(() -> {
                    loading.set(false);
                    retryDelayMs = RETRY_DELAY_MS;
                    if (loadedAd != null) {
                        return;
                    }
                    AdManager.releaseFromCache(nativeAd);
                    loadedAd = nativeAd;
                    ChatFlowLogger.event("ads", "native_loaded",
                            "unit=chat, hasHeadline=" + (nativeAd.getHeadline() != null));
                    notifyListeners();
                });
            }

            @Override
            public void onNativeAdFailedToLoad(@NonNull LoadAdError error) {
                mainHandler.post(() -> {
                    loading.set(false);
                    ChatFlowLogger.event("ads", "native_failed",
                            "unit=chat, code=" + error.getCode() + ", message=" + error.getMessage());
                    scheduleRetry();
                });
            }
        });
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** Libera o anúncio pendente (chamar em onDestroy do app quando necessário). */
    public void destroy() {
        retryScheduled.set(false);
        mainHandler.removeCallbacksAndMessages(null);
        NativeAd ad = loadedAd;
        loadedAd = null;
        if (ad != null) {
            ad.destroy();
        }
    }

    private void scheduleRetry() {
        if (!retryScheduled.compareAndSet(false, true)) {
            return;
        }
        long delay = retryDelayMs;
        retryDelayMs = Math.min(retryDelayMs * 2, RETRY_DELAY_MAX_MS);
        ChatFlowLogger.event("ads", "retry_scheduled", "delay_ms=" + delay);
        mainHandler.postDelayed(() -> {
            retryScheduled.set(false);
            requestAdIfNeeded();
        }, delay);
    }

    private void notifyListeners() {
        for (Listener listener : listeners) {
            try {
                listener.onNativeAdStateChanged();
            } catch (Exception ignored) {
            }
        }
    }
}
