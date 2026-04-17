package org.dpdns.sqxy090123.kit.vulnkit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent serviceIntent = new Intent(context, VulnService.class);
            // 使用 ContextCompat.startForegroundService 来自动处理 API 26+ 的兼容性问题
            ContextCompat.startForegroundService(context, serviceIntent);
        }
    }
}