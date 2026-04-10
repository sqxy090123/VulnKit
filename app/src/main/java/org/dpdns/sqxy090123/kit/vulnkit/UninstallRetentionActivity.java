package org.dpdns.sqxy090123.kit.vulnkit;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class UninstallRetentionActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 半透明背景，类似对话框
        getWindow().setGravity(Gravity.CENTER);
        getWindow().setLayout(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
        );

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 48, 48, 48);
        layout.setBackgroundColor(0xFF202020);

        TextView title = new TextView(this);
        title.setText("是否确定卸载？");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(20);
        layout.addView(title);

        TextView message = new TextView(this);
        message.setText("VulnKit 提供了强大的安全检测功能，您确定要移除吗？");
        message.setTextColor(0xFFCCCCCC);
        message.setPadding(0, 24, 0, 24);
        layout.addView(message);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);

        Button cancelBtn = new Button(this);
        cancelBtn.setText("取消");
        cancelBtn.setOnClickListener(v -> finish());
        buttons.addView(cancelBtn);

        Button uninstallBtn = new Button(this);
        uninstallBtn.setText("卸载");
        uninstallBtn.setOnClickListener(v -> {
            // 执行真正的卸载
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            finish();
        });
        buttons.addView(uninstallBtn);

        layout.addView(buttons);
        setContentView(layout);
    }

    // 厂商适配：通过透明主题和特定 intent-filter 触发
    // 在 AndroidManifest.xml 中配置
}