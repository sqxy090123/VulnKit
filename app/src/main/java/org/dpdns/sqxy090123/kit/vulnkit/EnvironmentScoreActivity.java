package org.dpdns.sqxy090123.kit.vulnkit;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class EnvironmentScoreActivity extends AppCompatActivity {
    private TextView tvScore;
    private TextView tvDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_env_score);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("环境评分");
        }

        tvScore = findViewById(R.id.tv_score);
        tvDetails = findViewById(R.id.tv_details);
        Button btnRefresh = findViewById(R.id.btn_refresh);

        btnRefresh.setOnClickListener(v -> refreshScore());
        refreshScore();
    }

    private void refreshScore() {
        EnvironmentSniffer.SniffResult result = EnvironmentSniffer.sniff(this);
        tvScore.setText(String.format("%d / %d\n%s", result.totalScore, result.maxScore, result.getScoreDescription()));

        StringBuilder sb = new StringBuilder();
        sb.append("=== 基础信息 ===\n");
        sb.append("内核版本: ").append(result.kernelVersion).append("\n");
        sb.append("安全补丁: ").append(result.securityPatch).append("\n");
        sb.append("构建指纹: ").append(result.buildFingerprint).append("\n\n");

        sb.append("=== 存储空间检测 ===\n");
        sb.append(result.storageUserType).append("\n\n");

        sb.append("=== 开发者选项 ===\n");
        sb.append("开发者模式: ").append(result.developerMode ? "开启" : "关闭").append("\n");
        sb.append("USB调试: ").append(result.adbEnabled ? "开启" : "关闭").append("\n");
        sb.append("无线调试: ").append(result.wirelessDebuggingEnabled ? "开启" : "关闭").append("\n\n");

        if (!result.runtimeTraces.isEmpty()) {
            sb.append("=== 运行时/注入痕迹 ===\n");
            for (String trace : result.runtimeTraces) {
                sb.append("- ").append(trace).append("\n");
            }
            sb.append("\n");
        }

        sb.append("=== 分区挂载状态 ===\n");
        for (String status : result.partitionMountStatus) {
            sb.append(status).append("\n");
        }
        sb.append("\n");

        sb.append("=== AVB / dm-verity ===\n");
        sb.append("AVB 状态: ").append(result.avbState).append(" (").append(result.avbVerified ? "锁定" : "解锁").append(")\n");
        sb.append("\n");

        if (!result.selinuxPolicyTraces.isEmpty()) {
            sb.append("=== SELinux 策略注入 ===\n");
            for (String trace : result.selinuxPolicyTraces) {
                sb.append("- ").append(trace).append("\n");
            }
            sb.append("\n");
        }

        if (!result.sandboxTraces.isEmpty()) {
            sb.append("=== 沙盒/模拟器检测 ===\n");
            for (String trace : result.sandboxTraces) {
                sb.append("- ").append(trace).append("\n");
            }
            sb.append("\n");
        }

        sb.append("=== 检测详情 ===\n");
        for (String detail : result.details) {
            sb.append(detail).append("\n");
        }

        if (!result.rootTraces.isEmpty()) {
            sb.append("\n=== Root 痕迹 ===\n");
            for (String trace : result.rootTraces) {
                sb.append("- ").append(trace).append("\n");
            }
        }

        tvDetails.setText(sb.toString());
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