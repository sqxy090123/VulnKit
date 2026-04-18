package org.dpdns.sqxy090123.kit.vulnkit;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

public class ShizukuAuthActivity extends AppCompatActivity {
    private static final String REQUIRED_PERMISSION = "org.dpdns.sqxy090123.kit.vulnkit.ACCESS_VULNERABLE_LIST";

    private ListView listView;
    private TextView tvStatus;
    private Button btnRequest;
    private AppListAdapter adapter;
    private List<AppInfo> appList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shizuku_auth);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Shizuku 授权管理");
        }

        tvStatus = findViewById(R.id.tv_status);
        listView = findViewById(R.id.list_view);
        btnRequest = findViewById(R.id.btn_request);

        adapter = new AppListAdapter();
        listView.setAdapter(adapter);

        btnRequest.setOnClickListener(v -> {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, "Shizuku 服务未运行", Toast.LENGTH_SHORT).show();
                return;
            }
            if (Shizuku.checkSelfPermission() != 0) {
                Shizuku.requestPermission(0);
                Toast.makeText(this, "请授予 Shizuku 权限", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "已获得 Shizuku 权限", Toast.LENGTH_SHORT).show();
            }
        });

        updateStatus();
        loadApps();
    }

    private void updateStatus() {
        boolean running = Shizuku.pingBinder();
        boolean granted = running && Shizuku.checkSelfPermission() == 0;
        tvStatus.setText("Shizuku: " + (running ? "运行中" : "未运行") +
                ", 权限: " + (granted ? "已授权" : "未授权"));
        tvStatus.setTextColor(granted ? Color.GREEN : Color.RED);
    }

    private void loadApps() {
        appList.clear();
        PackageManager pm = getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);

        for (PackageInfo pkg : packages) {
            if (pkg.requestedPermissions != null) {
                for (String perm : pkg.requestedPermissions) {
                    if (REQUIRED_PERMISSION.equals(perm)) {
                        AppInfo info = new AppInfo();
                        info.packageName = pkg.packageName;
                        info.appName = pkg.applicationInfo.loadLabel(pm).toString();
                        info.uid = pkg.applicationInfo.uid;
                        info.isSystem = (pkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                        appList.add(info);
                        break;
                    }
                }
            }
        }

        adapter.notifyDataSetChanged();
        tvStatus.append("\n声明权限的应用数: " + appList.size());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private static class AppInfo {
        String packageName;
        String appName;
        int uid;
        boolean isSystem;
    }

    private class AppListAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return appList.size();
        }

        @Override
        public Object getItem(int position) {
            return appList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, parent, false);
            }
            AppInfo info = appList.get(position);
            TextView text1 = convertView.findViewById(android.R.id.text1);
            TextView text2 = convertView.findViewById(android.R.id.text2);
            text1.setText(info.appName);
            text2.setText(info.packageName + " (UID: " + info.uid + ", " +
                    (info.isSystem ? "系统" : "用户") + ")");
            return convertView;
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