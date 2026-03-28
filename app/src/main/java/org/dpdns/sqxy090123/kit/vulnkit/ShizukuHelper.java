package org.dpdns.sqxy090123.kit.vulnkit;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

public class ShizukuHelper {
    private static final String TAG = "ShizukuHelper";
    private static final String PACKAGE_NAME = "org.dpdns.sqxy090123.kit.vulnkit";

    public static boolean isShizukuAvailable() {
        return Shizuku.isPreV11() || Shizuku.pingBinder();
    }

    public static boolean hasPermission() {
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestPermission() {
        if (isShizukuAvailable() && !hasPermission()) {
            Shizuku.requestPermission(0);
        }
    }

    /**
     * 由于 Shizuku 新版已移除 newProcess，改为引导用户手动通过 Shizuku Shell 执行命令
     */
    public static void grantWriteSecureSettings(Context context) {
        Toast.makeText(context,
                "请通过 Shizuku Shell 执行以下命令：\n" +
                        "pm grant " + PACKAGE_NAME + " android.permission.WRITE_SECURE_SETTINGS",
                Toast.LENGTH_LONG).show();
    }

    // 不再提供 executeAsShell 方法
}