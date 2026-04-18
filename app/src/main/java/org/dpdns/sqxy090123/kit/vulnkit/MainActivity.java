package org.dpdns.sqxy090123.kit.vulnkit;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;

import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE_BASE = 100;
    private int currentPermissionIndex = 0;
    private List<String> runtimePermissions = new ArrayList<>();
    private TerminalView terminalView;
    private Button btnRemoveAdmin;
    private DevicePolicyManager dpm;
    private ComponentName adminComponent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 安装 SplashScreen（系统自动管理，无需手动保持）
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        terminalView = findViewById(R.id.terminalView);
        btnRemoveAdmin = findViewById(R.id.btn_remove_admin);

        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, AdminReceiver.class);

        updateRemoveAdminButton();
        btnRemoveAdmin.setOnClickListener(v -> removeDeviceAdmin());

        collectRuntimePermissions();
        requestNextPermission();

        startService(new Intent(this, VulnService.class));
        terminalView.startShell();
    }

    private void collectRuntimePermissions() {
        runtimePermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        runtimePermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        runtimePermissions.add(Manifest.permission.CAMERA);
        runtimePermissions.add(Manifest.permission.RECORD_AUDIO);
        runtimePermissions.add(Manifest.permission.READ_CONTACTS);
        runtimePermissions.add(Manifest.permission.READ_SMS);
        runtimePermissions.add(Manifest.permission.SEND_SMS);
        runtimePermissions.add(Manifest.permission.READ_PHONE_STATE);
        runtimePermissions.add(Manifest.permission.POST_NOTIFICATIONS);

        List<String> needRequest = new ArrayList<>();
        for (String perm : runtimePermissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needRequest.add(perm);
            }
        }
        runtimePermissions = needRequest;
    }

    private void requestNextPermission() {
        if (currentPermissionIndex >= runtimePermissions.size()) {
            Toast.makeText(this, "所有必要权限已授予", Toast.LENGTH_SHORT).show();
            return;
        }
        String permission = runtimePermissions.get(currentPermissionIndex);
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            currentPermissionIndex++;
            requestNextPermission();
            return;
        }
        String permName = permission.substring(permission.lastIndexOf('.') + 1);
        Toast.makeText(this, "请求权限: " + permName, Toast.LENGTH_SHORT).show();
        ActivityCompat.requestPermissions(this,
                new String[]{permission},
                PERMISSION_REQUEST_CODE_BASE + currentPermissionIndex);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0) {
            String permission = permissions[0];
            String permName = permission.substring(permission.lastIndexOf('.') + 1);
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, permName + " 已授权", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, permName + " 被拒绝，部分功能可能受限", Toast.LENGTH_LONG).show();
            }
        }
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
        } else if (item.getItemId() == R.id.action_device_info) {
            startActivity(new Intent(this, DeviceInfoActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_env_score) {
            startActivity(new Intent(this, EnvironmentScoreActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_system_diagnose) {
            startActivity(new Intent(this, SystemDiagnoseActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_shizuku_auth) {
            startActivity(new Intent(this, ShizukuAuthActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_app_installer) {
            startActivity(new Intent(this, AppInstallerActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_permission_manager) {
            startActivity(new Intent(this, PermissionManagerActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRemoveAdminButton();
    }

    private void updateRemoveAdminButton() {
        boolean isAdmin = dpm.isAdminActive(adminComponent);
        btnRemoveAdmin.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
    }

    private void removeDeviceAdmin() {
        if (!dpm.isAdminActive(adminComponent)) {
            Toast.makeText(this, "当前不是设备管理员", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            dpm.removeActiveAdmin(adminComponent);
            Toast.makeText(this, "已取消设备管理员权限", Toast.LENGTH_SHORT).show();
            updateRemoveAdminButton();
        } catch (SecurityException e) {
            Toast.makeText(this, "移除失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}