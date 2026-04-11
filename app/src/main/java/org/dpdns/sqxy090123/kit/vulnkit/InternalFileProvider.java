package org.dpdns.sqxy090123.kit.vulnkit;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class InternalFileProvider extends ContentProvider {
    private static final String TAG = "InternalFileProvider";

    private static final Set<String> ALLOWED_PACKAGES = new HashSet<>(Arrays.asList(
            "bin.mt.plus",
            "bin.mt.plus.canary",
            "com.jecelyin.editor",
            "com.estrongs.android.pop",
            "com.ghisler.android.TotalCommander",
            "com.android.documentsui",
            "com.android.providers.downloads.ui",
            "com.google.android.documentsui"
    ));

    private static final String AUTHORITY = "org.dpdns.sqxy090123.kit.vulnkit.files";
    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    private static final int ROOT = 1;
    private static final int DIRECTORY = 2;
    private static final int FILE = 3;

    static {
        sUriMatcher.addURI(AUTHORITY, "/", ROOT);
        sUriMatcher.addURI(AUTHORITY, "/*", DIRECTORY);
        sUriMatcher.addURI(AUTHORITY, "/*/*", FILE);
    }

    private File rootDir;

    @Override
    public boolean onCreate() {
        // 根目录设为应用数据目录的父目录，即 /data/data
        rootDir = new File(getContext().getDataDir().getParent());
        Log.i(TAG, "Root directory: " + rootDir.getAbsolutePath());
        return true;
    }

    private void checkCallingPackage() {
        String callingPackage = getCallingPackage();
        if (callingPackage == null) return;
        if (!ALLOWED_PACKAGES.contains(callingPackage)) {
            throw new SecurityException("Unauthorized access: " + callingPackage);
        }
    }

    private File getFileForUri(Uri uri) {
        String path = uri.getPath();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return rootDir;
        }
        // 移除开头的斜杠，其余部分作为相对路径
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return new File(rootDir, path);
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        checkCallingPackage();

        File file = getFileForUri(uri);
        if (!file.exists()) {
            return null;
        }

        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_FLAGS
        });

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File child : children) {
                    addFileRow(cursor, child);
                }
            }
        } else {
            addFileRow(cursor, file);
        }
        return cursor;
    }

    private void addFileRow(MatrixCursor cursor, File file) {
        MatrixCursor.RowBuilder row = cursor.newRow();
        String documentId = getDocumentId(file);
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId);
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.getName());
        row.add(DocumentsContract.Document.COLUMN_SIZE, file.isFile() ? file.length() : 0);
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE,
                file.isDirectory() ? DocumentsContract.Document.MIME_TYPE_DIR : getMimeType(file.getName()));
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified());
        int flags = DocumentsContract.Document.FLAG_SUPPORTS_DELETE | DocumentsContract.Document.FLAG_SUPPORTS_RENAME;
        if (file.isDirectory()) {
            flags |= DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE;
        } else {
            flags |= DocumentsContract.Document.FLAG_SUPPORTS_WRITE;
        }
        row.add(DocumentsContract.Document.COLUMN_FLAGS, flags);
    }

    private String getDocumentId(File file) {
        String rootPath = rootDir.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        if (filePath.equals(rootPath)) {
            return "";
        }
        if (filePath.startsWith(rootPath)) {
            String rel = filePath.substring(rootPath.length());
            if (rel.startsWith("/")) {
                rel = rel.substring(1);
            }
            return rel;
        }
        return filePath;
    }

    private String getMimeType(String name) {
        String ext = android.webkit.MimeTypeMap.getFileExtensionFromUrl(name).toLowerCase();
        if (ext.isEmpty()) return "application/octet-stream";
        String mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime != null ? mime : "application/octet-stream";
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        File file = getFileForUri(uri);
        if (file.isDirectory()) {
            return DocumentsContract.Document.MIME_TYPE_DIR;
        }
        return getMimeType(file.getName());
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        checkCallingPackage();
        File file = getFileForUri(uri);
        if (!file.exists() || file.isDirectory()) {
            throw new FileNotFoundException();
        }
        int modeBits = ParcelFileDescriptor.parseMode(mode);
        return ParcelFileDescriptor.open(file, modeBits);
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        checkCallingPackage();
        // 简化实现：不支持创建
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        checkCallingPackage();
        File file = getFileForUri(uri);
        return deleteRecursive(file) ? 1 : 0;
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        return 0;
    }
}