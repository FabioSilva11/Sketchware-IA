package a.a.a;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import pro.sketchware.R;
import pro.sketchware.utility.StoragePermission;
import pro.sketchware.utility.TranslationFunction;

public abstract class DA extends qA {
    /**
     * Request code of an "All files access" request in progress, see {@link StoragePermission}.
     */
    private int pendingAllFilesAccessRequest = -1;

    public DA() {
    }

    private void requestStoragePermission(int requestCode) {
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
            if (c()) {
                b(requestCode);
            } else {
                e(requestCode);
            }
        }
    }

    public boolean a(int var1) {
        boolean var2 = c();
        if (!var2) {
            d(var1);
        }

        return var2;
    }

    public abstract void b(int var1);

    public abstract void c(int var1);

    public boolean c() {
        return StoragePermission.isGranted(requireContext());
    }

    public abstract void d();

    public void d(int var1) {
        if (!Sp.a) {
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(super.a);
            dialog.setTitle(R.string.common_message_permission_title_storage);
            dialog.setIcon(R.drawable.break_warning_96_red);
            dialog.setMessage(R.string.common_message_permission_storage);
            dialog.setPositiveButton(R.string.common_word_ok, (view, which) -> {
                if (!mB.a()) {
                    requestStoragePermission(var1);
                    view.dismiss();
                }
            });
            dialog.setNegativeButton(R.string.common_word_cancel, (view, which) -> {
                d();
                view.dismiss();
            });
            dialog.setOnDismissListener(dialog1 -> Sp.a = false);
            dialog.setCancelable(false);
            dialog.create().setCanceledOnTouchOutside(false);
            dialog.show();
            Sp.a = true;
        }
    }

    public abstract void e();

    public void e(int var1) {
        if (!Sp.a) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(super.a);
            builder.setTitle(R.string.common_message_permission_title_storage);
            builder.setIcon(R.drawable.break_warning_96_red);
            builder.setMessage(R.string.common_message_permission_storage1);
            builder.setPositiveButton(R.string.common_word_settings, (view, which) -> {
                if (!mB.a()) {
                    if (StoragePermission.usesAllFilesAccess()) {
                        requestStoragePermission(var1);
                    } else {
                        c(var1);
                    }
                    view.dismiss();
                }
            });
            builder.setNegativeButton(R.string.common_word_cancel, (view, which) -> {
                e();
                view.dismiss();
            });
            builder.setOnDismissListener(dialog1 -> Sp.a = false);
            builder.setCancelable(false);

            var dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
            Sp.a = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, @NonNull int[] grantResults) {
        for (String permission : permissions) {
            if ("android.permission.WRITE_EXTERNAL_STORAGE".equals(permission)) {
                if (grantResults.length == 0 || grantResults[0] != 0 || grantResults[1] != 0) {
                    e(requestCode);
                    break;
                }
                b(requestCode);
            }
        }

    }
}
