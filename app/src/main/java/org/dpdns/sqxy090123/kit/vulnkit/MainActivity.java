package org.dpdns.sqxy090123.kit.vulnkit;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE_BASE = 100;
    private int currentPermissionIndex = 0;
    private List<String> runtimePermissions = new ArrayList<>();
    private TerminalView terminalView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        terminalView = findViewById(R.id.terminalView);

        // 收集需要运行时请求的危险权限
        collectRuntimePermissions();

        // 开始轮番请求权限
        requestNextPermission();

        startService(new Intent(this, VulnService.class));

        // 启动终端
        terminalView.startShell();
    }

    /**
     * 收集所有需要运行时请求的危险权限（不包括普通权限和 signature 权限）
     */
    private void collectRuntimePermissions() {
        // 根据 Android 版本动态添加权限
        runtimePermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        runtimePermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        runtimePermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        runtimePermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        runtimePermissions.add(Manifest.permission.CAMERA);
        runtimePermissions.add(Manifest.permission.RECORD_AUDIO);
        runtimePermissions.add(Manifest.permission.READ_CONTACTS);
        runtimePermissions.add(Manifest.permission.READ_SMS);
        runtimePermissions.add(Manifest.permission.SEND_SMS);
        runtimePermissions.add(Manifest.permission.READ_PHONE_STATE);
        runtimePermissions.add(Manifest.permission.POST_NOTIFICATIONS); // Android 13+
        runtimePermissions.add(Manifest.permission.READ_MEDIA_IMAGES);  // Android 13+
        runtimePermissions.add(Manifest.permission.READ_MEDIA_VIDEO);   // Android 13+

        // Android 11+ 需要特殊处理 QUERY_ALL_PACKAGES（仅声明，无需运行时请求）
        // 其他普通权限如 INTERNET, WAKE_LOCK, FOREGROUND_SERVICE 等无需运行时请求

        // 移除已经授权的权限
        List<String> needRequest = new ArrayList<>();
        for (String perm : runtimePermissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needRequest.add(perm);
            }
        }
        runtimePermissions = needRequest;
    }

    /**
     * 请求下一个权限（轮番）
     */
    private void requestNextPermission() {
        if (currentPermissionIndex >= runtimePermissions.size()) {
            // 所有权限请求完毕
            Toast.makeText(this, "所有必要权限已授予", Toast.LENGTH_SHORT).show();
            return;
        }

        String permission = runtimePermissions.get(currentPermissionIndex);
        // 再次检查权限是否已授权（可能在前一次请求中用户已同意）
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            // 已授权，跳过并请求下一个
            currentPermissionIndex++;
            requestNextPermission();
            return;
        }

        // 显示当前请求的权限名称（简化显示）
        String permName = permission.substring(permission.lastIndexOf('.') + 1);
        Toast.makeText(this, "请求权限: " + permName, Toast.LENGTH_SHORT).show();

        // 请求权限（一次只请求一个）
        ActivityCompat.requestPermissions(this,
                new String[]{permission},
                PERMISSION_REQUEST_CODE_BASE + currentPermissionIndex);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // 处理当前请求的权限结果
        if (grantResults.length > 0) {
            String permission = permissions[0];
            String permName = permission.substring(permission.lastIndexOf('.') + 1);
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, permName + " 已授权", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, permName + " 被拒绝，部分功能可能受限", Toast.LENGTH_LONG).show();
            }
        }

        // 无论授权与否，继续请求下一个权限
        currentPermissionIndex++;
        requestNextPermission();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_vulnerabilities) {
            Intent intent = new Intent(this, VulnerabilityListActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}