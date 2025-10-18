package pro.sketchware.activities.main.fragments.projects_store;

import android.Manifest;
import android.app.DownloadManager;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import pro.sketchware.databinding.ActivityStoreProjectWebviewBinding;

public class ProjectWebViewActivity extends BaseAppCompatActivity {

    private static final int REQUEST_STORAGE = 1001;
    private ActivityStoreProjectWebviewBinding binding;
    private String initialUrl;
    private PendingDownload pendingDownload;

    private static class PendingDownload {
        final String url, userAgent, contentDisposition, mimeType;
        PendingDownload(String u, String ua, String cd, String mt) {
            url = u; userAgent = ua; contentDisposition = cd; mimeType = mt;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityStoreProjectWebviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initialUrl = getIntent().getStringExtra("url");
        setupWebView();
        setupSwipeRefresh();

        if (initialUrl != null) {
            binding.webView.loadUrl(initialUrl);
        }
    }

    private void setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener(() -> {
            binding.webView.reload();
        });
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void setupWebView() {
        WebView webView = binding.webView;

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadsImagesAutomatically(true);
        s.setBuiltInZoomControls(false);
        s.setSupportZoom(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        s.setSupportMultipleWindows(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                binding.swipeRefresh.setRefreshing(false);
                super.onPageFinished(view, url);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                if (ensureStoragePermission()) {
                    startDownload(url, userAgent, contentDisposition, mimeType);
                } else {
                    pendingDownload = new PendingDownload(url, userAgent, contentDisposition, mimeType);
                    Toast.makeText(ProjectWebViewActivity.this, "Permissões de armazenamento necessárias para baixar.", Toast.LENGTH_SHORT).show();
                }
            }
        });
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

    private void startDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle(fileName);
        request.setDescription("Baixando projeto do Sketchub");
        request.setMimeType(mimeType);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.addRequestHeader("User-Agent", userAgent);

        String cookie = CookieManager.getInstance().getCookie(url);
        if (cookie != null) {
            request.addRequestHeader("Cookie", cookie);
        }

        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
        request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName);

        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        dm.enqueue(request);
        Toast.makeText(this, "Download iniciado: " + fileName, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE) {
            boolean granted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted && pendingDownload != null) {
                startDownload(pendingDownload.url, pendingDownload.userAgent, pendingDownload.contentDisposition, pendingDownload.mimeType);
                pendingDownload = null;
            } else {
                Toast.makeText(this, "Permissões negadas; não é possível baixar.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}