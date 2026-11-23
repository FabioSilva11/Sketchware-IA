package pro.sketchware.activities.game.model;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.Nullable;

import java.io.File;

/**
 * Shared memory-safe sampled bitmap decoder used across the 2D Stage, Property Panel,
 * Logic Editor pickers, and Preview Runtime.
 */
public final class GameBitmapUtils {
    private GameBitmapUtils() {
    }

    @Nullable
    public static Bitmap decodeSampledFile(@Nullable File file, int maxDimensionPx) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }
        return decodeSampledPath(file.getAbsolutePath(), maxDimensionPx);
    }

    @Nullable
    public static Bitmap decodeSampledPath(@Nullable String path, int maxDimensionPx) {
        if (path == null || path.isEmpty() || maxDimensionPx <= 0) {
            return null;
        }
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);
            opts.inSampleSize = computeInSampleSize(opts.outWidth, opts.outHeight, maxDimensionPx);
            opts.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(path, opts);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static int computeInSampleSize(int rawWidth, int rawHeight, int maxDimensionPx) {
        int sample = 1;
        int maxSide = Math.max(rawWidth, rawHeight);
        while (maxSide / sample > maxDimensionPx) {
            sample *= 2;
        }
        return Math.max(1, sample);
    }
}
