package com.mvd.docsearchmvd;

import static com.mvd.docsearchmvd.util.Util.getStackTrace;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.JsPromptResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.content.*;
import android.os.Build;
import android.provider.Settings;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.EditText;
import android.widget.Toast;

import com.mvd.docsearchmvd.db.DatabaseManager;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    private DatabaseManager dbManager;

    public DatabaseManager getDbManager() {
        return dbManager;
    }

    private final ActivityResultLauncher<Intent> folderPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Log.d(WebAppInterface.TAG, "folderPickerLauncher");
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Log.d(WebAppInterface.TAG, "folderPickerLauncher: result is ok");
                    Uri treeUri = result.getData().getData();
                    String fullPath = getPathFromUri(treeUri);
                    if (fullPath != null) {
                        Log.d(WebAppInterface.TAG, "folder selected: " + fullPath);
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
        try {
            super.onCreate(savedInstanceState);
            Log.d(WebAppInterface.TAG, "onCreate called");
            setContentView(R.layout.activity_main);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Log.d(WebAppInterface.TAG, "Android 11+");
                if (!Environment.isExternalStorageManager()) {
                    Log.d(WebAppInterface.TAG, "the permission missing, starting intent");
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, 101); // API 30+
                    return;
                } else {
                    Log.d(WebAppInterface.TAG, "the permission exists");
                }
            } else {
                Log.d(WebAppInterface.TAG, "Android 10");
                if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(WebAppInterface.TAG, "the permission missing, requesting...");
                    requestPermissions(new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    }, 100); // API 29 и ниже
                    Log.d(WebAppInterface.TAG, "returning");
                    return;
                } else {
                    Log.d(WebAppInterface.TAG, "the permission exists");
                }
            }
            Log.d(WebAppInterface.TAG, "creating databaseManager instance...");
            dbManager = new DatabaseManager(this);
            dbManager.init(false);
            Log.d(WebAppInterface.TAG, "setupWebView is ready to be called");
            setupWebView();
        } catch (Exception e) {
            Log.e(WebAppInterface.TAG, e.getMessage() + "\n" + getStackTrace(e));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView == null || webView.getUrl() == null) {
            setupWebView();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
    }

    private void setupWebView() {
        try {
            Log.d(WebAppInterface.TAG, "setupWebView called");
            webView = findViewById(R.id.webview);
            if (webView == null) {
                Log.d(WebAppInterface.TAG, "webView is null in setupWebView()");
                return;
            }
            webView.loadUrl("about:blank");
            webView.getSettings().setJavaScriptEnabled(true);
            webView.getSettings().setDomStorageEnabled(true);
            webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            webView.addJavascriptInterface(new WebAppInterface(this, webView), "Android");
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    // Только если это вызов через PROCESS_TEXT
                    if (Intent.ACTION_PROCESS_TEXT.equals(getIntent().getAction())) {
                        String receivedText = getIntent().getStringExtra(Intent.EXTRA_PROCESS_TEXT);
                        if (receivedText != null && !receivedText.isEmpty()) {
                            injectTextIntoWebView(receivedText);
                        }
                    }
                }
            });
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onConsoleMessage(ConsoleMessage message) {
                    Log.d(WebAppInterface.TAG, message.message() + " @ " + message.lineNumber());
                    return true;
                }
                @Override
                public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                    final EditText input = new EditText(view.getContext());
                    input.setText(defaultValue);

                    new AlertDialog.Builder(view.getContext())
                            .setTitle("Ввод данных")
                            .setMessage(message)
                            .setView(input)
                            .setPositiveButton("OK", (dialog, which) -> result.confirm(input.getText().toString()))
                            .setNegativeButton("Отмена", (dialog, which) -> result.cancel())
                            .setCancelable(false)
                            .show();

                    return true;
                }
            });
            WebView.setWebContentsDebuggingEnabled(true);
            webView.clearCache(true);
            webView.loadUrl("file:///android_asset/index.html");
        } catch (Exception e) {
            Log.e(WebAppInterface.TAG, e.getMessage());
        }
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
        Log.d(WebAppInterface.TAG, "onRequestPermissionsResult");
        if (requestCode == 100) {
            Log.d(WebAppInterface.TAG, "request code 100");
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Log.d(WebAppInterface.TAG, "the permission granted");
                setupWebView();
            } else {
                Log.d(WebAppInterface.TAG, "the permission missing");
                Toast.makeText(this, "Необходимо разрешение для работы приложения", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleProcessTextIntent(intent);
    }

    private void handleProcessTextIntent(Intent intent) {
        if (Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())) {
            String receivedText = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT);
            if (receivedText != null && !receivedText.isEmpty()) {
                injectTextIntoWebView(receivedText);
            }
        }
    }

    private void injectTextIntoWebView(String receivedText) {
        String escaped = receivedText.replace("'", "\\'").replace("\n", " ");
        Log.d(WebAppInterface.TAG, "sending text to WebView: ");
        webView.evaluateJavascript("handleSearchFromAndroid('" + escaped + "')", null);
    }

}
