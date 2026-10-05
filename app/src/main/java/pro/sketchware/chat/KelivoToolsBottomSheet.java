package pro.sketchware.chat;

import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import pro.sketchware.R;

public final class KelivoToolsBottomSheet {

    public interface Callback {
        void onCamera();

        void onPhotos();

        void onUpload();
    }

    private KelivoToolsBottomSheet() {
    }

    public static void show(@NonNull ChatActivity activity, @NonNull Callback callback) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View content = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_kelivo_tools, null);
        dialog.setContentView(content);

        View camera = content.findViewById(R.id.tool_camera);
        View photos = content.findViewById(R.id.tool_photos);
        View upload = content.findViewById(R.id.tool_upload);

        if (camera != null) {
            camera.setOnClickListener(v -> {
                dialog.dismiss();
                callback.onCamera();
            });
        }
        if (photos != null) {
            photos.setOnClickListener(v -> {
                dialog.dismiss();
                callback.onPhotos();
            });
        }
        if (upload != null) {
            upload.setOnClickListener(v -> {
                dialog.dismiss();
                callback.onUpload();
            });
        }

        dialog.show();
    }
}
