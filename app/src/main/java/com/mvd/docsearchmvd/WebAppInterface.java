package com.mvd.docsearchmvd;

import android.content.*;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import com.mvd.docsearchmvd.db.DatabaseManager;
import com.mvd.docsearchmvd.indexer.FileIndexer;
import com.mvd.docsearchmvd.search.Hit;
import com.mvd.docsearchmvd.search.SearchEngine;

import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.List;

public class WebAppInterface {
    public static final String TAG = "DocSearchMvdLog";
    private Context context;
    WebAppInterface(Context ctx) {
        this.context = ctx;
    }
    @JavascriptInterface
    public String doSearch(String query) {
        String result = "";
        try {
            DatabaseManager db = new DatabaseManager(context);
            SearchEngine se = new SearchEngine(db);
            List<Hit> results = se.search(query);
            for (Hit hit : results) {
                result += hit.fileId + ": " + hit.hits + "<br>";
            }
            return result;
        } catch (Exception e) {
            return "Произошла ошибка поиска";
        }
    }

    @JavascriptInterface
    public String doIndex() {
        try {
            Log.d(TAG, "doIndex called");
            Uri folderUri = getFolderUri();
            if (folderUri == null) {
                return "Index source folder uri not found in the App's preferences";
            };
            DocumentFile folder = DocumentFile.fromTreeUri(context, folderUri);
            if (folder == null || !folder.exists() || !folder.isDirectory()) return "Invalid folder.";

            DatabaseManager db = new DatabaseManager(context);
            db.init(true);

            FileIndexer fileIndexer = new FileIndexer(db, context);

            DocumentFile[] folders = new DocumentFile[] { folder };
            fileIndexer.updateIndex(folders);

            return "Индекс " + folderUri.getPath() + "успешно завершен";
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            return "Ошибка при выполнении индекса.<br>" + sw.toString();
        }
    }

    private Uri getFolderUri() {
        SharedPreferences prefs = context.getSharedPreferences("docsearchmvd_prefs", Context.MODE_PRIVATE);
        String uriString = prefs.getString("folder_uri", null);
        return uriString != null ? Uri.parse(uriString) : null;
    }

    /*

Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
startActivity(intent);


Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
startActivityForResult(intent, REQUEST_CODE_PICK_FOLDER);

    * */

    /*
    @Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == REQUEST_CODE_PICK_FOLDER && resultCode == Activity.RESULT_OK) {
        Uri treeUri = data.getData();

        // Сохраняем постоянные права
        getContentResolver().takePersistableUriPermission(treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION |
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        // Преобразуем в DocumentFile и сохраняем в базе/конфиге
        DocumentFile pickedDir = DocumentFile.fromTreeUri(this, treeUri);
        // Далее — добавить в список путей и проиндексировать
    }
}
3. Хранение путей
Сохраняйте treeUri.toString() в базе или настройках. Это и будет уникальный идентификатор папки.

4. Повторное использование в будущем
    Uri uri = Uri.parse(savedUriString);
    DocumentFile dir = DocumentFile.fromTreeUri(context, uri);
    * */
}
