package pro.sketchware.store;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import pro.sketchware.store.core.Identity;
import pro.sketchware.store.core.StoreEngine;

/**
 * The store engine for the whole app. It starts in the background (creating the user's identity
 * takes a few seconds the first time), follows the network so traffic only flows when allowed, and
 * tells listeners on the main thread when anything changes.
 */
public final class StoreRuntime {

    private static final String TAG = "P2PStore";
    private static StoreRuntime instance;

    /** Set when a project was imported, so the projects list knows to reload. */
    public static volatile boolean projectsChanged;

    private final Context context;
    private final StoreSettings settings;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "store-runtime"));
    private final List<Runnable> listeners = new ArrayList<>();
    private final List<Consumer<StoreEngine>> pendingReady = new ArrayList<>();
    private volatile StoreEngine engine;
    private volatile String startError;
    private boolean starting;
    private WifiManager.MulticastLock multicastLock;
    private ConnectivityManager.NetworkCallback networkCallback;

    private StoreRuntime(Context context) {
        this.context = context.getApplicationContext();
        settings = new StoreSettings(this.context);
    }

    public static synchronized StoreRuntime get(Context context) {
        if (instance == null) {
            instance = new StoreRuntime(context);
        }
        return instance;
    }

    public StoreSettings settings() {
        return settings;
    }

    /** The running engine, or null while it starts. */
    public StoreEngine engine() {
        return engine;
    }

    public String startError() {
        return startError;
    }

    public File storeRoot() {
        return new File(context.getFilesDir(), "p2p_store");
    }

    /** Starts the engine if needed and hands it to {@code onReady} on the main thread. */
    public synchronized void start(Consumer<StoreEngine> onReady) {
        if (engine != null) {
            if (onReady != null) {
                StoreEngine ready = engine;
                mainHandler.post(() -> onReady.accept(ready));
            }
            return;
        }
        if (onReady != null) {
            pendingReady.add(onReady);
        }
        if (starting) {
            return;
        }
        starting = true;
        startError = null;
        worker.execute(() -> {
            try {
                File root = storeRoot();
                Identity identity = Identity.loadOrCreate(new File(root, "identity.key"));
                StoreEngine created = new StoreEngine(root, identity);
                created.setLogger(message -> Log.d(TAG, message));
                created.addListener(this::notifyListeners);
                // Decided before starting, so a metered connection isn't used even briefly
                created.node().setNetworkAllowed(isNetworkAllowed());
                created.start(settings.isSharing(), settings.getCacheLimitBytes());
                synchronized (this) {
                    engine = created;
                    starting = false;
                }
                mainHandler.post(() -> {
                    acquireMulticastLock();
                    watchNetwork();
                    List<Consumer<StoreEngine>> ready;
                    synchronized (StoreRuntime.this) {
                        ready = new ArrayList<>(pendingReady);
                        pendingReady.clear();
                    }
                    for (Consumer<StoreEngine> callback : ready) {
                        callback.accept(created);
                    }
                    notifyListeners();
                });
            } catch (Throwable e) {
                Log.e(TAG, "Couldn't start the P2P store", e);
                synchronized (this) {
                    starting = false;
                    startError = e.getMessage() == null ? e.toString() : e.getMessage();
                }
                notifyListeners();
            }
        });
    }

    /** Stops sharing and closes the network session (the data stays for the next start). */
    public synchronized void stop() {
        StoreEngine current = engine;
        engine = null;
        if (current != null) {
            worker.execute(current::stop);
        }
        releaseMulticastLock();
        unwatchNetwork();
        notifyListeners();
    }

    public void setSharing(boolean sharing) {
        settings.setSharing(sharing);
        StoreEngine current = engine;
        if (current != null) {
            worker.execute(() -> current.setSharing(sharing));
        }
    }

    public void setWifiOnly(boolean wifiOnly) {
        settings.setWifiOnly(wifiOnly);
        applyNetworkPolicy();
    }

    public void setCacheLimitMb(int megabytes) {
        settings.setCacheLimitMb(megabytes);
        StoreEngine current = engine;
        if (current != null) {
            current.setCacheLimit(settings.getCacheLimitBytes());
        }
    }

    // Listeners (main thread)

    public void addListener(Runnable listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeListener(Runnable listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners() {
        mainHandler.post(() -> {
            List<Runnable> copy;
            synchronized (listeners) {
                copy = new ArrayList<>(listeners);
            }
            for (Runnable listener : copy) {
                listener.run();
            }
        });
    }

    // Network policy

    private void watchNetwork() {
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        if (connectivity == null || networkCallback != null) {
            applyNetworkPolicy();
            return;
        }
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                applyNetworkPolicy();
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
                applyNetworkPolicy();
            }

            @Override
            public void onLost(@NonNull Network network) {
                applyNetworkPolicy();
            }
        };
        connectivity.registerDefaultNetworkCallback(networkCallback);
        applyNetworkPolicy();
    }

    private void unwatchNetwork() {
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        if (connectivity != null && networkCallback != null) {
            try {
                connectivity.unregisterNetworkCallback(networkCallback);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered
            }
        }
        networkCallback = null;
    }

    /** Whether the current connection may be used: any, or only unmetered ones in Wi-Fi only mode. */
    public boolean isNetworkAllowed() {
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        if (connectivity == null) {
            return true;
        }
        NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return false;
        }
        return !settings.isWifiOnly() || capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
    }

    private void applyNetworkPolicy() {
        StoreEngine current = engine;
        if (current == null) {
            return;
        }
        boolean allowed = isNetworkAllowed();
        if (allowed != current.node().isNetworkAllowed()) {
            worker.execute(() -> current.setNetworkAllowed(allowed));
        }
    }

    // Local network discovery needs multicast and broadcast packets, which Wi-Fi drivers filter by default

    private void acquireMulticastLock() {
        WifiManager wifi = context.getSystemService(WifiManager.class);
        if (wifi != null && multicastLock == null) {
            multicastLock = wifi.createMulticastLock("p2p-store");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        }
    }

    private void releaseMulticastLock() {
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }
        multicastLock = null;
    }
}
