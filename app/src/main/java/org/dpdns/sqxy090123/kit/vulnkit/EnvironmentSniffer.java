package org.dpdns.sqxy090123.kit.vulnkit;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 环境嗅探器 - 采集系统底层指标（无需 root）
 * 内核指标映射到应用层可读数据，参考 OPPO ColorOS 13 深度性能分析经验。
 */
public class EnvironmentSniffer {
    private static final String TAG = "EnvironmentSniffer";

    // ==================== 公开数据结构 ====================
    public static class MemInfo {
        public long totalRam;       // 总物理内存 (KB)
        public long freeRam;        // 空闲物理内存 (KB)
        public long availRam;       // 可用内存 (包括可回收缓存) (KB)
        public long cachedRam;      // 页缓存 (KB)
        public long swapTotal;      // Swap/ZRAM 总量 (KB)
        public long swapFree;       // Swap/ZRAM 空闲 (KB)

        public long getZramUsed() {
            return swapTotal - swapFree;
        }

        public float getZramUsageRatio() {
            if (swapTotal == 0) return 0f;
            return (float) getZramUsed() / swapTotal;
        }

        public float getAvailRatio() {
            if (totalRam == 0) return 0f;
            return (float) availRam / totalRam;
        }
    }

    public static class StorageStatus {
        public long totalBytes;
        public long freeBytes;
        public long writeSpeedKBps = -1; // -1 表示未测试
        public boolean ioSlow;
    }

    // ==================== 核心采集方法 ====================

    /**
     * 获取内存详细信息（解析 /proc/meminfo）
     * 映射内核指标：
     * - nr_free_pages -> MemFree
     * - SwapCached / nr_zspages -> SwapTotal - SwapFree (ZRAM 使用量)
     */
    public static MemInfo getMemInfo() {
        MemInfo info = new MemInfo();
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("MemTotal:"))
                    info.totalRam = extractKb(line);
                else if (line.startsWith("MemFree:"))
                    info.freeRam = extractKb(line);
                else if (line.startsWith("MemAvailable:"))
                    info.availRam = extractKb(line);
                else if (line.startsWith("Cached:"))
                    info.cachedRam = extractKb(line);
                else if (line.startsWith("SwapTotal:"))
                    info.swapTotal = extractKb(line);
                else if (line.startsWith("SwapFree:"))
                    info.swapFree = extractKb(line);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read /proc/meminfo", e);
        }
        // 降级：通过 ActivityManager 获取粗略值
        if (info.totalRam == 0) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            // 需要 Context，但静态方法无法获取，调用者需传入 Context，这里保留一个备用方法
        }
        return info;
    }

    private static long extractKb(String line) {
        String[] parts = line.split("\\s+");
        for (String part : parts) {
            if (part.matches("\\d+")) {
                return Long.parseLong(part);
            }
        }
        return 0;
    }

    /**
     * 获取 CPU 负载 (1/5/15 分钟)，读取 /proc/loadavg
     */
    public static float[] getCpuLoad() {
        float[] load = new float[]{0f, 0f, 0f};
        // 尝试直接读取 /proc/loadavg
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/loadavg"))) {
            String line = br.readLine();
            if (line != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    load[0] = Float.parseFloat(parts[0]);
                    load[1] = Float.parseFloat(parts[1]);
                    load[2] = Float.parseFloat(parts[2]);
                    return load;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Direct loadavg read failed, trying stat fallback");
        }

        // 后备：通过 /proc/stat 计算 CPU 使用率来估算负载
        float cpuUsage = getCpuUsageEstimate();
        if (cpuUsage >= 0) {
            int cores = getCpuCoreCount();
            float estimatedLoad = (cpuUsage / 100f) * cores;
            load[0] = estimatedLoad;
            load[1] = estimatedLoad * 0.9f;
            load[2] = estimatedLoad * 0.8f;
        }
        return load;
    }

    private static float getCpuUsageEstimate() {
        try {
            long[] stats1 = readCpuStat();
            if (stats1 == null) return -1f;
            Thread.sleep(100);
            long[] stats2 = readCpuStat();
            if (stats2 == null) return -1f;

            long total1 = 0, total2 = 0;
            long idle1 = stats1[3], idle2 = stats2[3];
            for (int i = 0; i < 8; i++) {
                total1 += stats1[i];
                total2 += stats2[i];
            }
            long totalDiff = total2 - total1;
            long idleDiff = idle2 - idle1;
            if (totalDiff == 0) return -1f;
            return 100f * (totalDiff - idleDiff) / totalDiff;
        } catch (Exception e) {
            return -1f;
        }
    }

    private static long[] readCpuStat() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = br.readLine();
            if (line != null && line.startsWith("cpu ")) {
                String[] parts = line.split("\\s+");
                long[] stats = new long[8];
                for (int i = 0; i < 8 && i+1 < parts.length; i++) {
                    stats[i] = Long.parseLong(parts[i+1]);
                }
                return stats;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to read /proc/stat");
        }
        return null;
    }

    public static boolean isCpuLoadValid() {
        float[] load = getCpuLoad();
        return load[0] > 0.01f;
    }

    /**
     * 获取 CPU 核心数
     */
    public static int getCpuCoreCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    /**
     * 获取系统开机时长 (毫秒)
     */
    public static long getSystemUptime() {
        return SystemClock.elapsedRealtime();
    }

    /**
     * 获取当前运行进程数 (通过 ActivityManager)
     */
    public static int getRunningProcessCount(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        return processes != null ? processes.size() : -1;
    }

    /**
     * 获取存储状态
     */
    public static StorageStatus getStorageStatus() {
        StorageStatus status = new StorageStatus();
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            status.totalBytes = stat.getBlockSizeLong() * stat.getBlockCountLong();
            status.freeBytes = stat.getBlockSizeLong() * stat.getAvailableBlocksLong();
        } catch (Exception e) {
            Log.e(TAG, "StatFs failed", e);
        }
        return status;
    }

    /**
     * 简单存储写入速度测试（写 1MB 数据到缓存目录）
     * 映射：f2fs discard 积压 -> 写入小文件耗时，低于 30MB/s 可能碎片严重
     */
    public static long testWriteSpeed(Context context) {
        File testFile = new File(context.getCacheDir(), "speed_test.tmp");
        byte[] buffer = new byte[1024 * 1024]; // 1MB
        long start = System.currentTimeMillis();
        try (FileOutputStream fos = new FileOutputStream(testFile)) {
            fos.write(buffer);
            fos.flush();
            long elapsed = System.currentTimeMillis() - start;
            testFile.delete();
            return (buffer.length / 1024) * 1000 / Math.max(elapsed, 1); // KB/s
        } catch (IOException e) {
            Log.e(TAG, "Write speed test failed", e);
            return -1;
        }
    }

    // ==================== 原有安全检测方法 (保留) ====================
    public static class SniffResult {
        public int totalScore;
        public int maxScore = 100;
        public boolean bootloaderUnlocked;
        public boolean selinuxPermissive;
        public boolean selinuxDisabled;
        public List<String> rootTraces = new ArrayList<>();
        public boolean debuggable;
        public boolean secureMode;
        public boolean systemReadWrite;
        public boolean isEmulator;
        public String kernelVersion;
        public String securityPatch;
        public String buildFingerprint;
        public List<String> details = new ArrayList<>();

        public String getScoreDescription() {
            if (totalScore >= 80) return "安全环境";
            if (totalScore >= 50) return "可疑环境";
            return "高风险环境";
        }
    }

    private static final int WEIGHT_BOOTLOADER = 25;
    private static final int WEIGHT_SELINUX = 20;
    private static final int WEIGHT_ROOT_TRACES = 25;
    private static final int WEIGHT_DEBUGGABLE = 15;
    private static final int WEIGHT_SYSTEM_RW = 10;
    private static final int WEIGHT_EMULATOR = 5;

    public static SniffResult sniff(Context context) {
        SniffResult result = new SniffResult();
        result.totalScore = 100;
        result.kernelVersion = getKernelVersion();
        result.securityPatch = Build.VERSION.SECURITY_PATCH;
        result.buildFingerprint = Build.FINGERPRINT;

        result.bootloaderUnlocked = isBootloaderUnlocked();
        if (result.bootloaderUnlocked) {
            result.totalScore -= WEIGHT_BOOTLOADER;
            result.details.add("⚠️ Bootloader 已解锁");
        } else result.details.add("✅ Bootloader 已锁定");

        String selinux = getSELinuxStatus();
        if ("Permissive".equalsIgnoreCase(selinux)) {
            result.selinuxPermissive = true;
            result.totalScore -= WEIGHT_SELINUX;
            result.details.add("⚠️ SELinux Permissive");
        } else if ("Disabled".equalsIgnoreCase(selinux)) {
            result.selinuxDisabled = true;
            result.totalScore -= WEIGHT_SELINUX;
            result.details.add("⚠️ SELinux 已禁用");
        } else result.details.add("✅ SELinux Enforcing");

        checkRootTraces(result);
        result.debuggable = "1".equals(getSystemProperty("ro.debuggable"));
        result.secureMode = "1".equals(getSystemProperty("ro.secure"));
        if (result.debuggable) {
            result.totalScore -= WEIGHT_DEBUGGABLE / 2;
            result.details.add("⚠️ ro.debuggable=1");
        }
        if (!result.secureMode) {
            result.totalScore -= WEIGHT_DEBUGGABLE / 2;
            result.details.add("⚠️ ro.secure=0");
        }
        if (!result.debuggable && result.secureMode) result.details.add("✅ 调试标志正常");

        result.systemReadWrite = isSystemReadWrite();
        if (result.systemReadWrite) {
            result.totalScore -= WEIGHT_SYSTEM_RW;
            result.details.add("⚠️ /system 可写");
        } else result.details.add("✅ /system 只读");

        result.isEmulator = isEmulator();
        if (result.isEmulator) {
            result.totalScore -= WEIGHT_EMULATOR;
            result.details.add("⚠️ 模拟器环境");
        } else result.details.add("✅ 真实设备");

        result.totalScore = Math.max(0, Math.min(100, result.totalScore));
        return result;
    }

    private static boolean isBootloaderUnlocked() {
        String[] props = {"ro.boot.verifiedbootstate", "ro.boot.flash.locked", "ro.boot.vbmeta.device_state"};
        for (String p : props) {
            String val = getSystemProperty(p);
            if (val != null && (val.contains("orange") || val.equals("0") || val.equals("unlocked")))
                return true;
        }
        String cmd = getKernelCommandLine();
        return cmd != null && cmd.contains("androidboot.verifiedbootstate=orange");
    }

    private static String getSELinuxStatus() {
        try (BufferedReader br = new BufferedReader(new FileReader("/sys/fs/selinux/enforce"))) {
            return "1".equals(br.readLine()) ? "Enforcing" : "Permissive";
        } catch (Exception e) { return "Unknown"; }
    }

    private static void checkRootTraces(SniffResult r) {
        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/data/local/su", "/system/app/SuperSU.apk"};
        for (String p : paths) if (new File(p).exists()) r.rootTraces.add(p);
        if (new File("/sbin/magisk").exists()) r.rootTraces.add("Magisk");
        if (!r.rootTraces.isEmpty()) {
            r.totalScore -= Math.min(WEIGHT_ROOT_TRACES, r.rootTraces.size() * 5);
            r.details.add("⚠️ Root 痕迹: " + r.rootTraces.size() + " 项");
        } else r.details.add("✅ 无 Root 痕迹");
    }

    private static boolean isSystemReadWrite() {
        File f = new File("/system/.rw_test");
        try { if (f.createNewFile()) { f.delete(); return true; } } catch (Exception ignored) {}
        return false;
    }

    private static boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("generic") ||
                Build.MODEL.contains("Emulator") ||
                Build.MANUFACTURER.contains("Genymotion");
    }

    private static String getKernelVersion() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/version"))) {
            String l = br.readLine();
            if (l != null) {
                int s = l.indexOf("version ") + 8;
                int e = l.indexOf(" ", s);
                if (e > s) return l.substring(s, e);
            }
        } catch (Exception ignored) {}
        return System.getProperty("os.version");
    }

    private static String getKernelCommandLine() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cmdline"))) {
            return br.readLine();
        } catch (Exception e) { return null; }
    }

    private static String getSystemProperty(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            return (String) c.getMethod("get", String.class).invoke(null, key);
        } catch (Exception e) { return null; }
    }
}