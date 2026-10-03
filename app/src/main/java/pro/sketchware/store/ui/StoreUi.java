package pro.sketchware.store.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.UUID;

import pro.sketchware.R;
import pro.sketchware.store.core.Records;

/**
 * Small helpers shared by the store screens.
 */
final class StoreUi {

    private StoreUi() {
    }

    static void load(ImageView view, File file, int placeholder) {
        if (file != null && file.isFile()) {
            Glide.with(view).load(file).placeholder(placeholder).into(view);
        } else if (placeholder != 0) {
            view.setImageResource(placeholder);
        } else {
            view.setImageDrawable(null);
        }
    }

    static String kindLabel(Context context, String kind) {
        return context.getString(Records.KIND_ANDROID_STUDIO.equals(kind)
                ? R.string.p2p_store_kind_android_studio : R.string.p2p_store_kind_sketchware);
    }

    static String size(Context context, long bytes) {
        return Formatter.formatShortFileSize(context, bytes);
    }

    static String date(Context context, long timestamp) {
        return DateFormat.getMediumDateFormat(context).format(new Date(timestamp));
    }

    static String shortId(String id) {
        return id == null || id.length() < 16 ? String.valueOf(id) : id.substring(0, 8) + "…" + id.substring(id.length() - 8);
    }

    static String count(long value) {
        if (value >= 1_000_000) {
            return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (value >= 10_000) {
            return (value / 1000) + "k";
        }
        return String.valueOf(value);
    }

    /**
     * Copies a picked image into the app's cache, scaled down so it fits the store's 2 MB limit for
     * media: PNG up to {@code maxSize} pixels for icons and avatars, JPEG for photos.
     */
    static File importImage(Context context, Uri uri, int maxSize, boolean png) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Not an image");
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(input, null, options);
        }
        if (bitmap == null) {
            throw new IOException("Not an image");
        }
        int longest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (longest > maxSize) {
            float scale = maxSize / (float) longest;
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * scale),
                    Math.round(bitmap.getHeight() * scale), true);
            bitmap.recycle();
            bitmap = scaled;
        }
        File folder = new File(context.getCacheDir(), "store_images");
        folder.mkdirs();
        File file = new File(folder, UUID.randomUUID() + (png ? ".png" : ".jpg"));
        try (FileOutputStream output = new FileOutputStream(file)) {
            bitmap.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 85, output);
        } finally {
            bitmap.recycle();
        }
        return file;
    }
}
