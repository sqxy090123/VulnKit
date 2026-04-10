package org.dpdns.sqxy090123.kit.vulnkit;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class VulnUserService extends Service {
    private static final String TAG = "VulnUserService";

    private final IVulnService.Stub binder = new IVulnService.Stub() {
        @Override
        public String executeCommand(String cmd) throws RemoteException {
            Log.d(TAG, "Executing: " + cmd);
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder out = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) out.append(line);
                while ((line = errReader.readLine()) != null) Log.e(TAG, "ERR: " + line);
                process.waitFor();
                return out.toString();
            } catch (Exception e) {
                Log.e(TAG, "Command failed", e);
                return null;
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}