package pro.sketchware.activities.main.fragments.projects_store;

import android.Manifest;
import android.app.DownloadManager;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.URLUtil;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import pro.sketchware.R;
import pro.sketchware.databinding.ActivityStoreProjectWebviewBinding;

public class ProjectWebViewActivity extends BaseAppCompatActivity {

    private static final int REQUEST_STORAGE = 1001;
    private static final long PROGRESS_POLL_DELAY_MS = 450L;

    private ActivityStoreProjectWebviewBinding binding;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private String initialUrl;
    private long downloadId = -1L;
    private String pendingDownloadUrl;

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (downloadId == -1L) {
                return;
            }
            if (updateDownloadProgress()) {
                progressHandler.postDelayed(this, PROGRESS_POLL_DELAY_MS);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityStoreProjectWebviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initialUrl = getIntent().getStringExtra("url");
        if (initialUrl == null || initialUrl.trim().isEmpty()) {
            binding.downloadTitle.setText(R.string.store_download_unavailable);
            binding.downloadStatus.setText(R.string.store_download_unavailable);
            binding.downloadProgress.setIndeterminate(false);
            return;
        }

        startOrRequestPermission(initialUrl);
    }

    @Override
    public void onDestroy() {
        progressHandler.removeCallbacks(progressRunnable);
        super.onDestroy();
    }

    private void startOrRequestPermission(String url) {
        if (ensureStoragePermission()) {
            startDownload(url);
        } else {
            pendingDownloadUrl = url;
            binding.downloadTitle.setText(R.string.store_download_waiting_permission);
            binding.downloadStatus.setText(R.string.store_download_permission_required);
        }
    }

    private boolean ensureStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }
        boolean write = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        if (write && read) return true;

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                REQUEST_STORAGE);
        return false;
    }

    private void startDownload(String url) {
        String fileName = URLUtil.guessFileName(url, null, null);
        binding.downloadFile.setText(fileName);
        binding.downloadTitle.setText(R.string.store_download_downloading);
        binding.downloadStatus.setText(R.string.store_download_starting);
        binding.downloadProgress.setIndeterminate(true);

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle(fileName);
        request.setDescription("Sketchware Store download");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
        request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName);

        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            binding.downloadTitle.setText(R.string.store_download_failed);
            binding.downloadStatus.setText(R.string.store_download_failed);
            binding.downloadProgress.setIndeterminate(false);
            return;
        }

        try {
            downloadId = downloadManager.enqueue(request);
        } catch (RuntimeException e) {
            binding.downloadTitle.setText(R.string.store_download_failed);
            binding.downloadStatus.setText(R.string.store_download_failed);
            binding.downloadProgress.setIndeterminate(false);
            return;
        }
        progressHandler.post(progressRunnable);
        Toast.makeText(this, getString(R.string.store_download_started, fileName), Toast.LENGTH_SHORT).show();
    }

    private boolean updateDownloadProgress() {
        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            return false;
        }

        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return true;
            }

            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            long downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

            updateProgressText(downloaded, total);

            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                binding.downloadProgress.setIndeterminate(false);
                binding.downloadProgress.setProgress(100);
                binding.downloadTitle.setText(R.string.store_download_complete);
                binding.downloadStatus.setText(R.string.store_download_complete_message);
                return false;
            }

            if (status == DownloadManager.STATUS_FAILED) {
                binding.downloadProgress.setIndeterminate(false);
                binding.downloadTitle.setText(R.string.store_download_failed);
                binding.downloadStatus.setText(R.string.store_download_failed);
                return false;
            }

            if (status == DownloadManager.STATUS_PAUSED) {
                binding.downloadStatus.setText(R.string.store_download_paused);
            }
            return true;
        }
    }

    private void updateProgressText(long downloaded, long total) {
        if (total > 0) {
            int progress = (int) Math.max(0, Math.min(100, downloaded * 100 / total));
            binding.downloadProgress.setIndeterminate(false);
            binding.downloadProgress.setProgress(progress);
            binding.downloadStatus.setText(getString(R.string.store_download_progress, progress, formatBytes(downloaded), formatBytes(total)));
        } else {
            binding.downloadProgress.setIndeterminate(true);
            binding.downloadStatus.setText(getString(R.string.store_download_progress_unknown, formatBytes(downloaded)));
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes / 1024d;
        if (value < 1024d) {
            return String.format(java.util.Locale.US, "%.1f KB", value);
        }
        value /= 1024d;
        if (value < 1024d) {
            return String.format(java.util.Locale.US, "%.1f MB", value);
        }
        return String.format(java.util.Locale.US, "%.1f GB", value / 1024d);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE) {
            boolean granted = grantResults.length > 0;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted && pendingDownloadUrl != null) {
                startDownload(pendingDownloadUrl);
                pendingDownloadUrl = null;
            } else {
                binding.downloadTitle.setText(R.string.store_download_failed);
                binding.downloadStatus.setText(R.string.store_download_permission_denied);
                binding.downloadProgress.setIndeterminate(false);
            }
        }
    }
}
