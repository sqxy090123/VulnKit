package org.dpdns.sqxy090123.kit.vulnkit;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * 系统健康诊断界面
 * 对外导出，可供其他应用调用（需权限）
 */
public class SystemDiagnoseActivity extends AppCompatActivity {
    private TextView tvScoreSummary;
    private TextView tvDetails;
    private Diagnose diagnose;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_system_diagnose);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("系统诊断");
        }

        tvScoreSummary = findViewById(R.id.tv_score_summary);
        tvDetails = findViewById(R.id.tv_details);
        Button btnRefresh = findViewById(R.id.btn_refresh);

        diagnose = new Diagnose(this);
        diagnose.startSampling(); // 启动后台采样

        btnRefresh.setOnClickListener(v -> runDiagnosis());
        runDiagnosis();
    }

    private void runDiagnosis() {
        Diagnose.DiagnoseResult result = diagnose.diagnose();

        // 更新顶部分数和状态
        String levelText;
        int color;
        switch (result.level) {
            case HEALTHY:
                levelText = "健康";
                color = Color.GREEN;
                break;
            case WARNING:
                levelText = "轻度压力";
                color = Color.YELLOW;
                break;
            case CRITICAL:
                levelText = "严重卡顿";
                color = Color.RED;
                break;
            default:
                levelText = "未知";
                color = Color.GRAY;
        }
        tvScoreSummary.setText(String.format("%d 分 (%s)", result.totalScore, levelText));
        tvScoreSummary.setTextColor(color);
        StringBuilder details = result.details;
        details.append("\n\n=== CPU 信息 ===\n");
        details.append(EnvironmentSniffer.getCpuInfo().toString());
        details.append("\n=== GPU 信息 ===\n");
        details.append(EnvironmentSniffer.getGpuInfo().toString());

        // 显示详细报告
        tvDetails.setText(result.details.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (diagnose != null) {
            diagnose.stopSampling();
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