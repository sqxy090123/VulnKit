package org.dpdns.sqxy090123.kit.vulnkit;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class DeviceInfoActivity extends AppCompatActivity {
    private TextView tvDeviceInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_info);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("设备信息");
        }

        tvDeviceInfo = findViewById(R.id.tv_device_info);
        Button btnRefresh = findViewById(R.id.btn_refresh);

        btnRefresh.setOnClickListener(v -> loadDeviceInfo());
        loadDeviceInfo();
    }

    private void loadDeviceInfo() {
        StringBuilder sb = new StringBuilder();
        try {
            // 基础系统信息
            sb.append("=== 系统信息 ===\n");
            sb.append("制造商: ").append(Build.MANUFACTURER).append("\n");
            sb.append("型号: ").append(Build.MODEL).append("\n");
            sb.append("产品: ").append(Build.PRODUCT).append("\n");
            sb.append("硬件: ").append(Build.HARDWARE).append("\n");
            sb.append("Android 版本: ").append(Build.VERSION.RELEASE)
                    .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
            sb.append("安全补丁: ").append(Build.VERSION.SECURITY_PATCH).append("\n");
            sb.append("构建指纹: ").append(Build.FINGERPRINT).append("\n\n");

            // Android ID
            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            sb.append("Android ID: ").append(androidId).append("\n\n");

            // 内核版本（纯文件读取，无命令执行）
            sb.append("=== 内核信息 ===\n");
            sb.append("内核版本: ").append(getKernelVersion()).append("\n");
            sb.append("架构: ").append(System.getProperty("os.arch")).append("\n\n");

            // 应用信息
            try {
                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                sb.append("=== 应用信息 ===\n");
                sb.append("版本名: ").append(pInfo.versionName).append("\n");
                sb.append("版本号: ").append(pInfo.versionCode).append("\n");
                sb.append("目标 SDK: ").append(pInfo.applicationInfo.targetSdkVersion).append("\n");
            } catch (PackageManager.NameNotFoundException e) {
                sb.append("无法获取应用信息\n");
            }

            sb.append("\n=== 其他 ===\n");
            sb.append("Is HarmonyOS: ").append(JNIInterface.isHarmonyOS()).append("\n");
            sb.append("Root Shell 可用: ").append(JNIInterface.isRootShellAvailable()).append("\n");
            // 移除会导致 seccomp 崩溃的 checkSetuidAvailable 调用
            // sb.append("setuid 可用: ").append(JNIInterface.checkSetuidAvailable()).append("\n");
            sb.append("setuid 可用: (未检测，避免 seccomp 崩溃)\n");

            tvDeviceInfo.setText(sb.toString());
        } catch (Exception e) {
            tvDeviceInfo.setText("加载设备信息时出错: " + e.getMessage());
        }
    }

    private String getKernelVersion() {
        // 方法1：读取 /proc/version（推荐，避免 seccomp）
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/version"))) {
            String line = reader.readLine();
            if (line != null) {
                // 提取 "Linux version x.x.x" 部分
                int start = line.indexOf("version ") + 8;
                int end = line.indexOf(" ", start);
                if (end > start) {
                    return line.substring(start, end);
                }
            }
        } catch (IOException ignored) {
        }

        // 方法2：读取系统属性（回退）
        String kernel = System.getProperty("os.version");
        return kernel != null ? kernel : "未知";
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