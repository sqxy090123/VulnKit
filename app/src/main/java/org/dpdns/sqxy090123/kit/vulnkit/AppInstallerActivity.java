package org.dpdns.sqxy090123.kit.vulnkit;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

public class AppInstallerActivity extends AppCompatActivity {
    private static final int MODE_SHIZUKU = 0;
    private static final int MODE_DHIZUKU = 1;
    private static final int MODE_ROOT = 2;

    private TextView tvStatus;
    private ProgressBar progressBar;
    private RadioGroup rgMode;
    private Button btnSelectApk;
    private int currentMode = MODE_SHIZUKU;

    private final ActivityResultLauncher<String> pickApkLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onApkSelected);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_installer);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("应用安装器");
        }

        tvStatus = findViewById(R.id.tv_status);
        progressBar = findViewById(R.id.progress);
        rgMode = findViewById(R.id.rg_mode);
        btnSelectApk = findViewById(R.id.btn_select_apk);

        rgMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_shizuku) currentMode = MODE_SHIZUKU;
            else if (checkedId == R.id.rb_dhizuku) currentMode = MODE_DHIZUKU;
            else if (checkedId == R.id.rb_root) currentMode = MODE_ROOT;
            updateStatus();
        });

        btnSelectApk.setOnClickListener(v -> pickApkLauncher.launch("application/vnd.android.package-archive"));

        updateStatus();
    }

    private void updateStatus() {
        String modeStr;
        boolean ready = false;
        switch (currentMode) {
            case MODE_SHIZUKU:
                modeStr = "Shizuku";
                ready = Shizuku.pingBinder() && Shizuku.checkSelfPermission() == 0;
                break;
            case MODE_DHIZUKU:
                modeStr = "Dhizuku";
                ready = checkDhizuku();
                break;
            case MODE_ROOT:
                modeStr = "Root";
                ready = checkRoot();
                break;
            default:
                modeStr = "未知";
        }
        tvStatus.setText("当前模式: " + modeStr + " | " + (ready ? "可用" : "不可用"));
        btnSelectApk.setEnabled(ready);
    }

    private boolean checkDhizuku() {
        try {
            PackageManager pm = getPackageManager();
            pm.getPackageInfo("com.rosan.dhizuku", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean checkRoot() {
        // 简单检测 su 二进制文件
        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su"};
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }
        return false;
    }

    private void onApkSelected(Uri uri) {
        if (uri == null) return;

        progressBar.setVisibility(ProgressBar.VISIBLE);
        btnSelectApk.setEnabled(false);

        new Thread(() -> {
            try {
                File cacheDir = getCacheDir();
                File tempApk = new File(cacheDir, "temp.apk");
                try (InputStream is = getContentResolver().openInputStream(uri);
                     FileOutputStream fos = new FileOutputStream(tempApk)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                    }
                }

                MalwareScanner scanner = new MalwareScanner();
                MalwareScanner.ScanResult result = scanner.scanApk(tempApk);

                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    btnSelectApk.setEnabled(true);

                    if (result.hasMalware) {
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("⚠️ 检测到恶意代码")
                                .setMessage("发现以下威胁:\n" + result.threats)
                                .setPositiveButton("取消", null)
                                .setNegativeButton("仍然安装", (d, w) -> installApk(tempApk))
                                .show();
                    } else {
                        Toast.makeText(this, "未检测到恶意代码", Toast.LENGTH_SHORT).show();
                        installApk(tempApk);
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    btnSelectApk.setEnabled(true);
                    Toast.makeText(this, "扫描失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void installApk(File apkFile) {
        switch (currentMode) {
            case MODE_SHIZUKU:
                installViaShizuku(apkFile);
                break;
            case MODE_DHIZUKU:
                installViaDhizuku(apkFile);
                break;
            case MODE_ROOT:
                installViaRoot(apkFile);
                break;
        }
    }

    @SuppressWarnings("deprecation")
    private void installViaShizuku(File apkFile) {
        try {
            String cmd = "pm install -r " + apkFile.getAbsolutePath();
            Method newProcess = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            newProcess.setAccessible(true);
            Process process = (Process) newProcess.invoke(null, new String[]{"sh", "-c", cmd}, null, null);
            int exitCode = process.waitFor();
            Toast.makeText(this, exitCode == 0 ? "安装成功" : "安装失败，code=" + exitCode, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "安装失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void installViaDhizuku(File apkFile) {
        try {
            Intent intent = new Intent("com.rosan.dhizuku.action.EXEC_COMMAND");
            intent.putExtra("command", "pm install -r " + apkFile.getAbsolutePath());
            intent.setPackage("com.rosan.dhizuku");
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Dhizuku 调用失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void installViaRoot(File apkFile) {
        try {
            Process process = Runtime.getRuntime().exec("su -c pm install -r " + apkFile.getAbsolutePath());
            int exitCode = process.waitFor();
            Toast.makeText(this, exitCode == 0 ? "安装成功" : "安装失败，code=" + exitCode, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "安装失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}