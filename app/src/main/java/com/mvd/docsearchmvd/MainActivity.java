package com.mvd.docsearchmvd;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.content.*;
import android.os.Build;
import android.provider.Settings;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;

import com.mvd.docsearchmvd.db.DatabaseManager;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    private final ActivityResultLauncher<Intent> folderPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Log.d("MVDMainActivity", "folderPickerLauncher");
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Log.d("MVDMainActivity", "folderPickerLauncher: result is ok");
                    Uri treeUri = result.getData().getData();
                    String fullPath = getPathFromUri(treeUri);
                    if (fullPath != null) {
                        Log.d("MVDMainActivity", "folder selected: " + fullPath);
                        webView.evaluateJavascript("onFolderChosen(" + JSONObject.quote(fullPath) + ")", null);
                    }
                }
            });

    public void selectFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        folderPickerLauncher.launch(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("MVDMainActivity", "onCreate called");
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.d("MVDMainActivity", "Android 11+");
            if (!Environment.isExternalStorageManager()) {
                Log.d("MVDMainActivity", "the permission missing, starting intent");
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, 101); // API 30+
                return;
            } else {
                Log.d("MVDMainActivity", "the permission exists");
            }
        } else {
            Log.d("MVDMainActivity", "Android 10");
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.d("MVDMainActivity", "the permission missing, requesting...");
                requestPermissions(new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE
                }, 100); // API 29 и ниже
                Log.d("MVDMainActivity", "returning");
                return;
            } else {
                Log.d("MVDMainActivity", "the permission exists");
            }
        }
        Log.d("MVDMainActivity", "setupWebView is ready to be called");
        setupWebView();
    }

    private void setupWebView() {
        Log.d("MVDMainActivity", "setupWebView called");
        webView = findViewById(R.id.webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.addJavascriptInterface(new WebAppInterface(this, webView), "Android");
        webView.setWebViewClient(new WebViewClient());
        WebView.setWebContentsDebuggingEnabled(true);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                Log.d(WebAppInterface.TAG, message.message() + " @ " + message.lineNumber());
                return true;
            }
        });
        webView.clearCache(true);
        DatabaseManager db = new DatabaseManager(this);
        db.init(false);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private String getPathFromUri(Uri uri) {
        if (uri == null) return null;
        String docId = DocumentsContract.getTreeDocumentId(uri);
        String[] parts = docId.split(":");
        if (parts.length == 2) {
            String type = parts[0];
            String relPath = parts[1];
            if ("primary".equalsIgnoreCase(type)) {
                return Environment.getExternalStorageDirectory() + "/" + relPath;
            } else {
                return "/storage/" + type + "/" + relPath;
            }
        } else if (parts.length == 1) {
            return Environment.getExternalStorageDirectory().getAbsolutePath();
        }
        return null;
    }

    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 101) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    setupWebView();
                } else {
                    Toast.makeText(this, "Требуется доступ ко всем файлам", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d("MVDMainActivity", "onRequestPermissionsResult");
        if (requestCode == 100) {
            Log.d("MVDMainActivity", "request code 100");
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Log.d("MVDMainActivity", "the permission granted");
                setupWebView(); // Всё хорошо, продолжаем
            } else {
                Log.d("MVDMainActivity", "the permission missing");
                Toast.makeText(this, "Необходимо разрешение для работы приложения", Toast.LENGTH_LONG).show();
            }
        }
    }

}
