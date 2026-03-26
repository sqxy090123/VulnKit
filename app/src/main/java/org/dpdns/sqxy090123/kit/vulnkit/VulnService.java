package org.dpdns.sqxy090123.kit.vulnkit;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class VulnService extends Service {
    private static final String TAG = "VulnService";
    private final IVulnService.Stub binder = new IVulnService.Stub() {
        @Override
        public String executeCommand(String cmd) throws RemoteException {
            // 实际执行命令并返回结果（简化）
            return "执行结果（暂未实现）";
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "VulnService started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}