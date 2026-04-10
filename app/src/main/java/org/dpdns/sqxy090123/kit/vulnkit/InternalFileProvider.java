package org.dpdns.sqxy090123.kit.vulnkit;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class InternalFileProvider extends ContentProvider {
    private static final String TAG = "InternalFileProvider";
    // 白名单包名（MT 管理器及其变体）
    private static final Set<String> ALLOWED_PACKAGES = new HashSet<>(Arrays.asList(
            "bin.mt.plus",
            "bin.mt.plus.canary",
            "com.jecelyin.editor"
    ));

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        // 校验调用者包名
        String callingPackage = getCallingPackage();
        if (!ALLOWED_PACKAGES.contains(callingPackage)) {
            Log.w(TAG, "Access denied for package: " + callingPackage);
            throw new SecurityException("Unauthorized access");
        }

        // 解析文件路径
        String path = uri.getPath();
        if (path == null) throw new FileNotFoundException("Invalid URI");

        // 映射到内部存储根目录
        File rootDir = new File(getContext().getFilesDir().getParentFile(), ".");
        File targetFile = new File(rootDir, path);

        try {
            // 规范路径，防止路径遍历攻击
            String canonicalRoot = rootDir.getCanonicalPath();
            String canonicalTarget = targetFile.getCanonicalPath();
            if (!canonicalTarget.startsWith(canonicalRoot)) {
                throw new SecurityException("Path traversal detected");
            }
        } catch (IOException e) {
            throw new FileNotFoundException("Failed to resolve path: " + e.getMessage());
        }

        if (!targetFile.exists()) {
            throw new FileNotFoundException("File not found: " + targetFile.getAbsolutePath());
        }

        int modeFlags = ParcelFileDescriptor.parseMode(mode);
        return ParcelFileDescriptor.open(targetFile, modeFlags);
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        // 不支持目录列表，返回 null
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        return 0;
    }
}