package pro.sketchware.chat;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads small chat thumbnails without decoding the original image on the UI thread. */
final class ChatImageThumbnailLoader {
    private static final String TAG = "ChatThumbnailLoader";
    private static final int MAX_CACHE_KB = 8 * 1024;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "chat-thumbnail-loader");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(MAX_CACHE_KB) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getAllocationByteCount() / 1024);
        }
    };
    private static final Object WAITERS_LOCK = new Object();
    private static final Map<String, List<Target>> WAITERS = new HashMap<>();
    private static final Map<String, Boolean> FAILED = new HashMap<>();

    private ChatImageThumbnailLoader() {
    }

    static void load(ImageView image, Uri uri, int requestedSizePx, int placeholderRes) {
        if (image == null) {
            return;
        }
        image.setImageResource(placeholderRes);
        if (uri == null) {
            image.setTag(null);
            return;
        }

        int targetSize = Math.max(64, requestedSizePx);
        String key = uri.toString() + "#" + targetSize;
        image.setTag(key);
        Bitmap cached = CACHE.get(key);
        if (cached != null && !cached.isRecycled()) {
            image.setImageBitmap(cached);
            return;
        }

        Context appContext = image.getContext().getApplicationContext();
        synchronized (WAITERS_LOCK) {
            if (Boolean.TRUE.equals(FAILED.get(key))) {
                return;
            }
            List<Target> targets = WAITERS.get(key);
            if (targets != null) {
                targets.add(new Target(image, key));
                return;
            }
            targets = new ArrayList<>();
            targets.add(new Target(image, key));
            WAITERS.put(key, targets);
        }

        DECODER.execute(() -> decodeAndDeliver(appContext, uri, targetSize, key));
    }

    private static void decodeAndDeliver(Context context, Uri uri, int targetSize, String key) {
        Bitmap bitmap = null;
        boolean permanentFailure = false;
        try {
            bitmap = decodeSampled(context.getContentResolver(), uri, targetSize);
        } catch (SecurityException | FileNotFoundException denied) {
            permanentFailure = true;
            Log.w(TAG, "Imagem sem permissão persistente: " + uri);
        } catch (Exception error) {
            Log.w(TAG, "Não foi possível gerar miniatura: " + uri, error);
        }

        if (bitmap != null) {
            CACHE.put(key, bitmap);
        }

        final Bitmap delivered = bitmap;
        final List<Target> targets;
        synchronized (WAITERS_LOCK) {
            targets = WAITERS.remove(key);
            if (permanentFailure) {
                // Referências antigas podem ter perdido a concessão do DocumentsUI.
                // Não consulte o provider repetidamente durante cada rebind.
                FAILED.put(key, true);
            }
        }
        if (targets == null || delivered == null) {
            return;
        }
        MAIN.post(() -> {
            for (Target target : targets) {
                ImageView image = target.image.get();
                if (image != null && target.key.equals(image.getTag())) {
                    image.setImageBitmap(delivered);
                }
            }
        });
    }

    private static Bitmap decodeSampled(ContentResolver resolver, Uri uri, int targetSize)
            throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = resolver.openInputStream(uri)) {
            if (stream == null) {
                throw new FileNotFoundException(uri.toString());
            }
            BitmapFactory.decodeStream(stream, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new FileNotFoundException("Imagem inválida: " + uri);
        }

        int sampleSize = 1;
        while (bounds.outWidth / (sampleSize * 2) >= targetSize
                && bounds.outHeight / (sampleSize * 2) >= targetSize) {
            sampleSize *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sampleSize);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream stream = resolver.openInputStream(uri)) {
            if (stream == null) {
                throw new FileNotFoundException(uri.toString());
            }
            Bitmap bitmap = BitmapFactory.decodeStream(stream, null, options);
            if (bitmap == null) {
                throw new FileNotFoundException("Imagem inválida: " + uri);
            }
            return bitmap;
        }
    }

    private static final class Target {
        final WeakReference<ImageView> image;
        final String key;

        Target(ImageView image, String key) {
            this.image = new WeakReference<>(image);
            this.key = key;
        }
    }
}
