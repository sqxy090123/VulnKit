package org.dpdns.sqxy090123.kit.vulnkit;

import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PermissionManagerActivity extends AppCompatActivity {
    private static final String MANAGE_PERMISSION = "org.dpdns.sqxy090123.kit.vulnkit.MANAGE_BY_USER";
    private static final String[] MANAGEABLE_PERMISSIONS = {
            "org.dpdns.sqxy090123.kit.vulnkit.ACCESS_VULNERABLE_LIST",
            "org.dpdns.sqxy090123.kit.vulnkit.ACCESS_ALL_INFO",
            "org.dpdns.sqxy090123.kit.vulnkit.ACCESS_DEVICE_INFO",
            "org.dpdns.sqxy090123.kit.vulnkit.ACCESS_ENV_SCORE",
            "org.dpdns.sqxy090123.kit.vulnkit.ACCESS_ENV_SCORE_DETAIL",
            "org.dpdns.sqxy090123.kit.vulnkit.ACCESS_DIAGNOSE"
    };

    private ExpandableListView expandableListView;
    private AppListAdapter adapter;
    private List<AppInfo> appList = new ArrayList<>();
    private Map<String, Set<String>> grantedPermissions = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_manager);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("权限精细化管理");
        }

        expandableListView = findViewById(R.id.expandable_list);
        Button btnRefresh = findViewById(R.id.btn_refresh);

        adapter = new AppListAdapter();
        expandableListView.setAdapter(adapter);

        btnRefresh.setOnClickListener(v -> loadApps());

        loadApps();
    }

    private void loadApps() {
        appList.clear();
        PackageManager pm = getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);

        for (PackageInfo pkg : packages) {
            if (pkg.requestedPermissions != null) {
                AppInfo info = new AppInfo();
                info.packageName = pkg.packageName;
                info.appName = pkg.applicationInfo.loadLabel(pm).toString();
                info.uid = pkg.applicationInfo.uid;
                info.icon = pkg.applicationInfo.loadIcon(pm);
                info.isSystem = (pkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

                Set<String> requested = new HashSet<>();
                Set<String> granted = new HashSet<>();
                for (int i = 0; i < pkg.requestedPermissions.length; i++) {
                    String perm = pkg.requestedPermissions[i];
                    for (String mp : MANAGEABLE_PERMISSIONS) {
                        if (mp.equals(perm) || MANAGE_PERMISSION.equals(perm)) {
                            requested.add(perm);
                            if ((pkg.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                                granted.add(perm);
                            }
                        }
                    }
                }

                if (!requested.isEmpty()) {
                    info.requestedPermissions = requested;
                    info.grantedPermissions = granted;
                    appList.add(info);
                }
            }
        }

        adapter.notifyDataSetChanged();
    }

    private void showPermissionDetailDialog(String packageName, String permission) {
        String description;
        switch (permission) {
            case "org.dpdns.sqxy090123.kit.vulnkit.MANAGE_BY_USER":
                description = "管理权限：允许应用管理其他自定义权限的授权状态。\n此权限为前置权限。";
                break;
            case "org.dpdns.sqxy090123.kit.vulnkit.ACCESS_VULNERABLE_LIST":
                description = "漏洞列表访问权限：允许应用读取可利用漏洞列表。";
                break;
            case "org.dpdns.sqxy090123.kit.vulnkit.ACCESS_ALL_INFO":
                description = "全部信息访问权限：允许应用读取所有设备及系统信息。";
                break;
            case "org.dpdns.sqxy090123.kit.vulnkit.ACCESS_DEVICE_INFO":
                description = "设备信息访问权限：允许应用读取基本设备信息。";
                break;
            case "org.dpdns.sqxy090123.kit.vulnkit.ACCESS_ENV_SCORE":
                description = "环境评分访问权限：允许应用读取环境安全评分。";
                break;
            case "org.dpdns.sqxy090123.kit.vulnkit.ACCESS_ENV_SCORE_DETAIL":
                description = "环境评分详情权限：允许应用读取环境安全评分详细报告。";
                break;
            case "org.dpdns.sqxy090123.kit.vulnkit.ACCESS_DIAGNOSE":
                description = "系统诊断权限：允许应用执行系统诊断并读取报告。";
                break;
            default:
                description = "自定义权限。";
        }

        new AlertDialog.Builder(this)
                .setTitle("权限详情")
                .setMessage(description)
                .setPositiveButton("确定", null)
                .show();
    }

    private class AppInfo {
        String packageName;
        String appName;
        int uid;
        android.graphics.drawable.Drawable icon;
        boolean isSystem;
        Set<String> requestedPermissions = new HashSet<>();
        Set<String> grantedPermissions = new HashSet<>();

        boolean hasManagePermission() {
            return grantedPermissions.contains(MANAGE_PERMISSION);
        }
    }

    private class AppListAdapter extends BaseExpandableListAdapter {
        @Override
        public int getGroupCount() {
            return appList.size();
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            AppInfo info = appList.get(groupPosition);
            return info.requestedPermissions.size();
        }

        @Override
        public Object getGroup(int groupPosition) {
            return appList.get(groupPosition);
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            AppInfo info = appList.get(groupPosition);
            List<String> perms = new ArrayList<>(info.requestedPermissions);
            return perms.get(childPosition);
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(PermissionManagerActivity.this)
                        .inflate(R.layout.item_permission_group, parent, false);
            }
            AppInfo info = appList.get(groupPosition);
            TextView tvName = convertView.findViewById(R.id.tv_app_name);
            TextView tvPackage = convertView.findViewById(R.id.tv_package);
            TextView tvBadge = convertView.findViewById(R.id.tv_badge);

            tvName.setText(info.appName);
            tvPackage.setText(info.packageName);
            if (info.hasManagePermission()) {
                tvBadge.setText("已授权管理");
                tvBadge.setTextColor(Color.GREEN);
            } else {
                tvBadge.setText("未授权管理");
                tvBadge.setTextColor(Color.RED);
            }

            return convertView;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(PermissionManagerActivity.this)
                        .inflate(R.layout.item_permission_child, parent, false);
            }
            AppInfo info = appList.get(groupPosition);
            List<String> perms = new ArrayList<>(info.requestedPermissions);
            String perm = perms.get(childPosition);

            TextView tvPerm = convertView.findViewById(R.id.tv_permission);
            CheckBox cbGranted = convertView.findViewById(R.id.cb_granted);
            View btnInfo = convertView.findViewById(R.id.btn_info);

            tvPerm.setText(formatPermissionName(perm));
            cbGranted.setChecked(info.grantedPermissions.contains(perm));
            cbGranted.setEnabled(info.hasManagePermission()); // 只有被授权管理后才能修改

            btnInfo.setOnClickListener(v -> showPermissionDetailDialog(info.packageName, perm));

            cbGranted.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // 这里应该调用系统 API 授予/撤销权限（需要系统权限）
                // 作为演示，仅更新 UI
                Toast.makeText(PermissionManagerActivity.this,
                        "需要系统权限才能修改", Toast.LENGTH_SHORT).show();
                cbGranted.setChecked(!isChecked);
            });

            return convertView;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }

        private String formatPermissionName(String permission) {
            if (MANAGE_PERMISSION.equals(permission)) {
                return "管理权限 (前置)";
            }
            String[] parts = permission.split("\\.");
            return parts[parts.length - 1].replace("ACCESS_", "").replace("_", " ");
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