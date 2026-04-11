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
            sb.append("=== 系统信息 ===\n");
            sb.append("制造商: ").append(Build.MANUFACTURER).append("\n");
            sb.append("型号: ").append(Build.MODEL).append("\n");
            sb.append("产品: ").append(Build.PRODUCT).append("\n");
            sb.append("硬件: ").append(Build.HARDWARE).append("\n");
            sb.append("Android 版本: ").append(Build.VERSION.RELEASE)
                    .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
            sb.append("安全补丁: ").append(Build.VERSION.SECURITY_PATCH).append("\n");
            sb.append("构建指纹: ").append(Build.FINGERPRINT).append("\n\n");

            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            sb.append("Android ID: ").append(androidId).append("\n\n");

            sb.append("=== 内核信息 ===\n");
            sb.append("内核版本: ").append(getKernelVersion()).append("\n");
            sb.append("架构: ").append(System.getProperty("os.arch")).append("\n\n");

            // CPU 信息
            EnvironmentSniffer.CpuInfo cpu = EnvironmentSniffer.getCpuInfo();
            sb.append("=== CPU 信息 ===\n");
            sb.append(cpu.toString()).append("\n");

            // GPU 信息
            EnvironmentSniffer.GpuInfo gpu = EnvironmentSniffer.getGpuInfo();
            sb.append("=== GPU 信息 ===\n");
            sb.append(gpu.toString()).append("\n");

            try {
                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                sb.append("=== 应用信息 ===\n");
                sb.append("版本名: ").append(pInfo.versionName).append("\n");
                // 将原来的 versionCode 行改为：
                long versionCode = 0;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    versionCode = pInfo.getLongVersionCode();
                } else {
                    versionCode = pInfo.versionCode;
                }
                sb.append("版本号: ").append(versionCode).append("\n");
                sb.append("目标 SDK: ").append(pInfo.applicationInfo.targetSdkVersion).append("\n");
            } catch (PackageManager.NameNotFoundException e) {
                sb.append("无法获取应用信息\n");
            }

            sb.append("\n=== 其他 ===\n");
            sb.append("Is HarmonyOS: ").append(JNIInterface.isHarmonyOS()).append("\n");
            sb.append("Root Shell 可用: ").append(JNIInterface.isRootShellAvailable()).append("\n");
            sb.append("setuid 可用: (未检测，避免 seccomp 崩溃)\n");

            tvDeviceInfo.setText(sb.toString());
        } catch (Exception e) {
            tvDeviceInfo.setText("加载设备信息时出错: " + e.getMessage());
        }
    }

    private String getKernelVersion() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/version"))) {
            String line = reader.readLine();
            if (line != null) {
                int start = line.indexOf("version ") + 8;
                int end = line.indexOf(" ", start);
                if (end > start) return line.substring(start, end);
            }
        } catch (IOException ignored) {}
        return System.getProperty("os.version");
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