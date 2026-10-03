package com.besome.sketch.lib.base;

import android.Manifest;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import a.a.a.Sp;
import a.a.a.mB;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.utility.StoragePermission;
import pro.sketchware.utility.TranslationFunction;

public abstract class BasePermissionAppCompatActivity extends BaseAppCompatActivity {

    /**
     * Request code of an "All files access" request in progress. That one is granted in system
     * settings, so the result is checked when the user comes back instead of in
     * {@link #onRequestPermissionsResult(int, String[], int[])}.
     */
    private int pendingAllFilesAccessRequest = -1;

    public boolean f(int i) {
        boolean j = isStoragePermissionGranted();
        if (!j) {
            i(i);
        }
        return j;
    }

    public abstract void g(int i);

    public abstract void h(int i);

    public void i(int i) {
        if (!Sp.a) {
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
            dialog.setTitle(Helper.getResString(R.string.common_message_permission_title_storage));
            dialog.setIcon(R.drawable.break_warning_96_red);
            dialog.setMessage(Helper.getResString(R.string.common_message_permission_storage));
            dialog.setPositiveButton(Helper.getResString(R.string.common_word_ok), (v, which) -> {
                if (!mB.a()) {
                    requestStoragePermission(i);
                    v.dismiss();
                }
            });
            dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), (v, which) -> {
                l();
                v.dismiss();
            });
            dialog.setOnDismissListener(dialog1 -> Sp.a = false);
            dialog.setCancelable(false);
            // dialog.setCanceledOnTouchOutside(false);
            dialog.show();
            Sp.a = true;
        }
    }

    /**
     * Asks for storage access; {@link #g(int)} runs once it's granted.
     */
    protected void requestStoragePermission(int requestCode) {
        if (StoragePermission.usesAllFilesAccess()) {
            pendingAllFilesAccessRequest = requestCode;
        }
        StoragePermission.request(this, requestCode);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (pendingAllFilesAccessRequest != -1) {
            int requestCode = pendingAllFilesAccessRequest;
            pendingAllFilesAccessRequest = -1;
            if (isStoragePermissionGranted()) {
                g(requestCode);
            } else {
                j(requestCode);
            }
        }
    }

    public abstract void l();

    public abstract void m();

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (String str : permissions) {
            if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(str)) {
                if (grantResults.length > 0 && grantResults[0] == 0 && grantResults[1] == 0) {
                    g(requestCode);
                } else {
                    j(requestCode);
                    return;
                }
            }
        }
    }

    public void j(int i) {
        if (!Sp.a) {
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
            dialog.setTitle(Helper.getResString(R.string.common_message_permission_title_storage));
            dialog.setIcon(R.drawable.break_warning_96_red);
            dialog.setMessage(Helper.getResString(R.string.common_message_permission_storage1));
            dialog.setPositiveButton(Helper.getResString(R.string.common_word_settings), (v, which) -> {
                if (!mB.a()) {
                    if (StoragePermission.usesAllFilesAccess()) {
                        requestStoragePermission(i);
                    } else {
                        h(i);
                    }
                    v.dismiss();
                }
            });
            dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), (v, which) -> {
                m();
                v.dismiss();
            });
            dialog.setOnDismissListener(dialog1 -> Sp.a = false);
            dialog.setCancelable(false);
            // dialog.setCanceledOnTouchOutside(false);
            dialog.show();
            Sp.a = true;
        }
    }
}
