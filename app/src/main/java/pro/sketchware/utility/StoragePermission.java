package pro.sketchware.utility;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

/**
 * Access to shared storage (/sdcard/.sketchware), where projects, libraries and builds live.
 * <p>
 * Since Sketchware targets API 30+, Android 11+ only grants that through "All files access"
 * (MANAGE_EXTERNAL_STORAGE), which is toggled in system settings: there is no
 * onRequestPermissionsResult callback, callers check {@link #isGranted(Context)} again once the
 * user comes back (e.g. in onResume). Older versions keep using the storage runtime permissions.
 */
public final class StoragePermission {

    private StoragePermission() {
    }

    /**
     * @return Whether the system settings screen is used instead of a permission dialog
     */
    public static boolean usesAllFilesAccess() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    public static boolean isGranted(Context context) {
        if (usesAllFilesAccess()) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public static void request(Activity activity, int requestCode) {
        if (usesAllFilesAccess()) {
            FileUtil.requestAllFilesAccessPermission(activity);
        } else {
            ActivityCompat.requestPermissions(activity, runtimePermissions(), requestCode);
        }
    }

    public static void request(Fragment fragment, int requestCode) {
        if (usesAllFilesAccess()) {
            FileUtil.requestAllFilesAccessPermission(fragment.requireContext());
        } else {
            fragment.requestPermissions(runtimePermissions(), requestCode);
        }
    }

    private static String[] runtimePermissions() {
        return new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
    }
}
