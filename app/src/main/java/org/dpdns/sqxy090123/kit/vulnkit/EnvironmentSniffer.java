package org.dpdns.sqxy090123.kit.vulnkit;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EnvironmentSniffer {
    private static final String TAG = "EnvironmentSniffer";

    // ==================== 公开数据结构 ====================
    public static class MemInfo {
        public long totalRam;
        public long freeRam;
        public long availRam;
        public long cachedRam;
        public long swapTotal;
        public long swapFree;

        public long getZramUsed() { return swapTotal - swapFree; }
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
        public long writeSpeedKBps = -1;
        public boolean ioSlow;
    }

    public static class CpuInfo {
        public String hardware;
        public String processor;
        public int cores;
        public String maxFrequency;
        public String minFrequency;
        public String currentFrequency;
        public String bogoMips;
        public String features;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("型号: ").append(hardware != null ? hardware : "未知").append("\n");
            sb.append("处理器: ").append(processor != null ? processor : "未知").append("\n");
            sb.append("核心数: ").append(cores).append("\n");
            if (maxFrequency != null) sb.append("最大频率: ").append(maxFrequency).append(" MHz\n");
            if (minFrequency != null) sb.append("最小频率: ").append(minFrequency).append(" MHz\n");
            if (currentFrequency != null) sb.append("当前频率: ").append(currentFrequency).append(" MHz\n");
            if (bogoMips != null) sb.append("BogoMIPS: ").append(bogoMips).append("\n");
            if (features != null && !features.isEmpty()) sb.append("特性: ").append(features).append("\n");
            return sb.toString();
        }
    }

    public static class GpuInfo {
        public String vendor;
        public String model;
        public String renderer;
        public String version;
        public String frequency;
        public String maxFrequency;
        public String minFrequency;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (vendor != null) sb.append("厂商: ").append(vendor).append("\n");
            if (model != null) sb.append("型号: ").append(model).append("\n");
            if (renderer != null) sb.append("渲染器: ").append(renderer).append("\n");
            if (version != null) sb.append("OpenGL ES 版本: ").append(version).append("\n");
            if (maxFrequency != null) sb.append("最大频率: ").append(maxFrequency).append(" MHz\n");
            if (minFrequency != null) sb.append("最小频率: ").append(minFrequency).append(" MHz\n");
            if (frequency != null) sb.append("当前频率: ").append(frequency).append(" MHz\n");
            if (sb.length() == 0) sb.append("无法获取 GPU 信息\n");
            return sb.toString();
        }
    }

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

        // 存储空间类型检测结果
        public String storageUserType;
        public boolean allUsersAccessible;

        // 开发者选项
        public boolean developerMode;
        public boolean adbEnabled;
        public boolean wirelessDebuggingEnabled;

        // Hook框架
        public List<String> hookTraces = new ArrayList<>();

        // 沙盒/模拟器
        public boolean isSandboxed;
        public List<String> sandboxTraces = new ArrayList<>();

        // 新增检测项
        public List<String> runtimeTraces = new ArrayList<>();       // 运行时痕迹（与hookTraces合并展示）
        public List<String> partitionMountStatus = new ArrayList<>();// 分区挂载状态
        public boolean avbVerified;                                  // AVB验证状态（true=绿色/锁定）
        public String avbState;                                      // 详细AVB状态描述
        public List<String> selinuxPolicyTraces = new ArrayList<>(); // SELinux策略注入痕迹

        public String getScoreDescription() {
            if (totalScore >= 80) return "安全环境";
            if (totalScore >= 50) return "可疑环境";
            return "高风险环境";
        }
    }

    // ==================== 核心采集方法 ====================

    public static MemInfo getMemInfo() {
        MemInfo info = new MemInfo();
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("MemTotal:")) info.totalRam = extractKb(line);
                else if (line.startsWith("MemFree:")) info.freeRam = extractKb(line);
                else if (line.startsWith("MemAvailable:")) info.availRam = extractKb(line);
                else if (line.startsWith("Cached:")) info.cachedRam = extractKb(line);
                else if (line.startsWith("SwapTotal:")) info.swapTotal = extractKb(line);
                else if (line.startsWith("SwapFree:")) info.swapFree = extractKb(line);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read /proc/meminfo", e);
        }
        return info;
    }

    private static long extractKb(String line) {
        String[] parts = line.split("\\s+");
        for (String part : parts) {
            if (part.matches("\\d+")) return Long.parseLong(part);
        }
        return 0;
    }

    public static float[] getCpuLoad() {
        float[] load = new float[]{0f, 0f, 0f};
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

    public static int getCpuCoreCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    public static long getSystemUptime() {
        return SystemClock.elapsedRealtime();
    }

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

    public static long testWriteSpeed(Context context) {
        File testFile = new File(context.getCacheDir(), "speed_test.tmp");
        byte[] buffer = new byte[1024 * 1024];
        long start = System.currentTimeMillis();
        try (FileOutputStream fos = new FileOutputStream(testFile)) {
            fos.write(buffer);
            fos.flush();
            long elapsed = System.currentTimeMillis() - start;
            testFile.delete();
            return (buffer.length / 1024) * 1000 / Math.max(elapsed, 1);
        } catch (IOException e) {
            Log.e(TAG, "Write speed test failed", e);
            return -1;
        }
    }

    // ==================== 存储空间类型检测 ====================
    public static String getStorageUserType(SniffResult result) {
        File emulated = new File("/storage/emulated");
        if (!emulated.exists() || !emulated.isDirectory()) {
            return "未知 (无 /storage/emulated)";
        }

        List<Integer> accessibleUsers = new ArrayList<>();
        for (int i = 0; i <= 999; i++) {
            File userDir = new File(emulated, String.valueOf(i));
            if (userDir.exists() && userDir.isDirectory()) {
                if (userDir.canRead() && userDir.list() != null) {
                    accessibleUsers.add(i);
                }
            }
        }

        result.allUsersAccessible = (accessibleUsers.size() > 5);

        StringBuilder sb = new StringBuilder();
        if (accessibleUsers.isEmpty()) {
            sb.append("无法访问任何用户目录");
        } else {
            sb.append("可访问的用户目录: ").append(accessibleUsers).append("\n");
            if (accessibleUsers.contains(0)) {
                sb.append("→ 包含主用户 (0)");
                if (accessibleUsers.contains(10)) sb.append(" + 工作资料/炼妖壶 (10)");
                if (accessibleUsers.contains(999)) sb.append(" + OPPO/OnePlus 分身 (999)");
                if (accessibleUsers.contains(128)) sb.append(" + 华为分身 (128)");
            } else {
                sb.append("→ 当前非主用户，用户ID: ").append(accessibleUsers);
            }
        }

        if (result.allUsersAccessible) {
            sb.append("\n⚠️ 所有用户目录均可访问，可能存在 root 权限或为模拟器环境");
        }

        return sb.toString();
    }

    // ==================== CPU 信息 ====================
    public static CpuInfo getCpuInfo() {
        CpuInfo info = new CpuInfo();
        info.cores = getCpuCoreCount();

        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            StringBuilder featuresBuilder = new StringBuilder();
            while ((line = br.readLine()) != null) {
                if (line.startsWith("Hardware") && info.hardware == null) {
                    info.hardware = line.split(":")[1].trim();
                } else if (line.startsWith("Processor") && info.processor == null) {
                    info.processor = line.split(":")[1].trim();
                } else if (line.startsWith("BogoMIPS") && info.bogoMips == null) {
                    info.bogoMips = line.split(":")[1].trim();
                } else if (line.startsWith("Features")) {
                    featuresBuilder.append(line.split(":")[1].trim());
                }
            }
            info.features = featuresBuilder.toString();
        } catch (IOException e) {
            Log.e(TAG, "Failed to read /proc/cpuinfo", e);
        }

        info.maxFrequency = readFileFirstLine("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
        info.minFrequency = readFileFirstLine("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq");
        info.currentFrequency = readFileFirstLine("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");

        if (info.maxFrequency != null) info.maxFrequency = String.valueOf(Integer.parseInt(info.maxFrequency) / 1000);
        if (info.minFrequency != null) info.minFrequency = String.valueOf(Integer.parseInt(info.minFrequency) / 1000);
        if (info.currentFrequency != null) info.currentFrequency = String.valueOf(Integer.parseInt(info.currentFrequency) / 1000);

        if (info.hardware == null) info.hardware = Build.HARDWARE;
        if (info.processor == null) info.processor = "未知";

        return info;
    }

    // ==================== GPU 信息 ====================
    public static GpuInfo getGpuInfo() {
        GpuInfo info = new GpuInfo();
        info.vendor = getSystemProperty("ro.gles.vendor");
        info.renderer = getSystemProperty("ro.gles.renderer");
        info.version = getSystemProperty("ro.gles.version");

        String[] modelPaths = {
                "/sys/class/kgsl/kgsl-3d0/gpu_model",
                "/sys/kernel/gpu/gpu_model",
                "/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0/gpu_model",
        };
        for (String path : modelPaths) {
            String model = readFileFirstLine(path);
            if (model != null && !model.isEmpty()) {
                info.model = model.trim();
                break;
            }
        }

        String[] freqPaths = {
                "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
                "/sys/class/kgsl/kgsl-3d0/gpuclk",
                "/sys/kernel/gpu/gpu_clock",
        };
        for (String path : freqPaths) {
            String freq = readFileFirstLine(path);
            if (freq != null && !freq.isEmpty()) {
                try { info.frequency = String.valueOf(Long.parseLong(freq.trim()) / 1000000); } catch(Exception ignored){}
                break;
            }
        }

        info.maxFrequency = readFileFirstLine("/sys/class/kgsl/kgsl-3d0/max_gpuclk");
        if (info.maxFrequency != null) try { info.maxFrequency = String.valueOf(Long.parseLong(info.maxFrequency) / 1000000); } catch(Exception e){}
        info.minFrequency = readFileFirstLine("/sys/class/kgsl/kgsl-3d0/min_gpuclk");
        if (info.minFrequency != null) try { info.minFrequency = String.valueOf(Long.parseLong(info.minFrequency) / 1000000); } catch(Exception e){}

        return info;
    }

    private static String readFileFirstLine(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            return br.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    // ==================== 辅助方法 ====================
    private static String readFileContent(String path) {
        try {
            return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)));
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> readAllLines(String path) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) lines.add(line);
        } catch (IOException e) {
            // ignore
        }
        return lines;
    }

    private static Map<String, String> parseMounts() {
        Map<String, String> result = new HashMap<>();
        for (String line : readAllLines("/proc/mounts")) {
            String[] parts = line.split(" ");
            if (parts.length >= 4) {
                result.put(parts[1], parts[3]);
            }
        }
        return result;
    }

    // ==================== 新增检测方法 ====================

    private static void detectDeveloperOptions(Context context, SniffResult result) {
        try {
            result.developerMode = Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
        } catch (Exception e) {
            result.developerMode = "1".equals(getSystemProperty("persist.sys.developer_options"));
        }

        try {
            result.adbEnabled = Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.ADB_ENABLED, 0) != 0;
        } catch (Exception e) {
            result.adbEnabled = "1".equals(getSystemProperty("persist.sys.usb.config"))
                    || "1".equals(getSystemProperty("sys.usb.config"));
        }

        if (Build.VERSION.SDK_INT >= 30) {
            try {
                result.wirelessDebuggingEnabled = Settings.Global.getInt(context.getContentResolver(),
                        "adb_wifi_enabled", 0) != 0;
            } catch (Exception e) {
                result.wirelessDebuggingEnabled = false;
            }
        }
    }

    private static void detectRuntimeAndHookFrameworks(Context context, SniffResult result) {
        // 合并运行时痕迹与Hook痕迹（统一展示）
        List<String> traces = result.runtimeTraces;
        Set<String> hookPkgs = new HashSet<>(Arrays.asList(
                "de.robv.android.xposed.installer", "com.saurik.substrate",
                "org.lsposed.manager", "com.topjohnwu.magisk",
                "com.ryzenrise.corepatcher", "com.windyhook.android"
        ));
        PackageManager pm = context.getPackageManager();
        for (String pkg : hookPkgs) {
            try {
                pm.getPackageInfo(pkg, 0);
                traces.add("已安装框架包: " + pkg);
            } catch (PackageManager.NameNotFoundException ignored) {}
        }

        // 扫描 /proc/self/maps
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String lower = line.toLowerCase();
                String module = extractModuleName(line);
                if (lower.contains("frida") || lower.contains("gum-")) {
                    traces.add("Frida 注入 (" + module + ")");
                }
                if (lower.contains("xposed") || lower.contains("edxposed") || lower.contains("lsposed")) {
                    traces.add("Xposed 框架 (" + module + ")");
                }
                if (lower.contains("substrate")) {
                    traces.add("Substrate 框架 (" + module + ")");
                }
                if (lower.contains("riru") || lower.contains("zygisk")) {
                    traces.add("Riru/Zygisk (" + module + ")");
                }
                if (lower.contains("magisk")) {
                    traces.add("Magisk 模块 (" + module + ")");
                }
                if (lower.contains("sandhook") || lower.contains("epic") || lower.contains("pine")) {
                    traces.add("其他 Hook 框架 (" + module + ")");
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to read /proc/self/maps");
        }

        // 去重
        result.hookTraces = new ArrayList<>(new HashSet<>(traces));
        result.runtimeTraces = result.hookTraces;
    }

    private static String extractModuleName(String mapsLine) {
        String[] parts = mapsLine.split("\\s+");
        if (parts.length >= 6) {
            return new File(parts[5]).getName();
        }
        return "unknown";
    }

    private static void detectPartitionMountStatus(SniffResult result) {
        Map<String, String> mounts = parseMounts();
        String[] checkPoints = {"/system", "/vendor", "/product", "/system_ext"};
        List<String> status = new ArrayList<>();
        for (String mp : checkPoints) {
            String opts = mounts.get(mp);
            if (opts != null) {
                boolean rw = opts.contains("rw");
                status.add(mp + ": " + (rw ? "可读写 (rw)" : "只读 (ro)"));
                if (rw) {
                    result.details.add("⚠️ " + mp + " 以可读写模式挂载");
                    result.totalScore = Math.max(0, result.totalScore - 5);
                }
            } else {
                status.add(mp + ": 未挂载");
            }
        }
        // 检测可疑挂载（Magisk）
        for (Map.Entry<String, String> e : mounts.entrySet()) {
            if (e.getKey().contains("magisk") || e.getValue().contains("magisk")) {
                status.add("⚠️ 发现 Magisk 挂载点: " + e.getKey());
                result.totalScore = Math.max(0, result.totalScore - 10);
            }
        }
        result.partitionMountStatus = status;
    }

    private static void detectAvbAndDmVerity(SniffResult result) {
        String verifiedboot = getSystemProperty("ro.boot.verifiedbootstate");
        if (verifiedboot == null) verifiedboot = getSystemProperty("ro.boot.vbmeta.device_state");
        String cmdline = getKernelCommandLine();
        if (cmdline != null) {
            if (cmdline.contains("androidboot.verifiedbootstate=orange")) verifiedboot = "orange";
            else if (cmdline.contains("androidboot.verifiedbootstate=yellow")) verifiedboot = "yellow";
            else if (cmdline.contains("androidboot.verifiedbootstate=green")) verifiedboot = "green";
        }

        result.avbState = (verifiedboot != null) ? verifiedboot : "未知";
        result.avbVerified = "green".equals(verifiedboot);

        // dm-verity状态
        String verityMode = getSystemProperty("ro.boot.veritymode");
        if (verityMode == null) {
            verityMode = getSystemProperty("ro.boot.verity_mode");
        }
        boolean enforcing = "enforcing".equalsIgnoreCase(verityMode);
        if (!enforcing && !"未知".equals(result.avbState)) {
            result.details.add("⚠️ dm-verity 未强制开启 (" + verityMode + ")");
        }

        if (!result.avbVerified) {
            result.details.add("⚠️ AVB 验证状态非绿色 (" + result.avbState + ")");
            result.totalScore = Math.max(0, result.totalScore - 20);
        }
    }

    private static void detectSelinuxPolicyTraces(SniffResult result) {
        List<String> traces = new ArrayList<>();
        // 检查常见Magisk SELinux模块目录
        String[] modulePaths = {
                "/data/adb/modules",
                "/data/adb/modules_update",
                "/sbin/.magisk/modules"
        };
        for (String base : modulePaths) {
            File dir = new File(base);
            if (dir.exists() && dir.isDirectory()) {
                File[] modules = dir.listFiles();
                if (modules != null) {
                    for (File mod : modules) {
                        if (mod.getName().toLowerCase().contains("selinux") ||
                                new File(mod, "post-fs-data.sh").exists()) {
                            traces.add("SELinux模块目录: " + mod.getAbsolutePath());
                        }
                    }
                }
            }
        }
        // 检查sepolicy注入工具
        String[] injectTools = {"/data/local/tmp/sepolicy-inject", "/system/bin/sepolicy-inject"};
        for (String path : injectTools) {
            if (new File(path).exists()) {
                traces.add("sepolicy注入工具: " + path);
            }
        }
        result.selinuxPolicyTraces = traces;
        if (!traces.isEmpty()) {
            result.totalScore = Math.max(0, result.totalScore - 10);
            result.details.add("⚠️ 发现SELinux策略注入痕迹");
        }
    }

    private static void detectSandbox(SniffResult result) {
        // 与之前版本相同，略作优化
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/1/cgroup"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String lower = line.toLowerCase();
                if (lower.contains("docker") || lower.contains("lxc") || lower.contains("kubepods")) {
                    result.sandboxTraces.add("检测到容器环境: " + line);
                    result.isSandboxed = true;
                }
            }
        } catch (IOException ignored) {}

        String[] qemuProps = {"ro.kernel.qemu", "init.svc.qemud", "init.svc.qemu-props"};
        for (String prop : qemuProps) {
            String val = getSystemProperty(prop);
            if (val != null && !val.isEmpty() && !"0".equals(val)) {
                result.sandboxTraces.add("模拟器属性: " + prop + "=" + val);
                result.isSandboxed = true;
            }
        }

        String[] emuFiles = {
                "/init.vbox86.rc",
                "/init.ranchu.rc",
                "/system/bin/qemu-props",
                "/dev/socket/qemud",
                "/system/lib/libc_malloc_debug_qemu.so",
                "/sys/qemu_trace"
        };
        for (String path : emuFiles) {
            if (new File(path).exists()) {
                result.sandboxTraces.add("模拟器文件存在: " + path);
                result.isSandboxed = true;
            }
        }

        String hardware = Build.HARDWARE.toLowerCase();
        if (hardware.contains("goldfish") || hardware.contains("ranchu") ||
                hardware.contains("vbox") || hardware.contains("qemu")) {
            result.sandboxTraces.add("模拟器硬件: " + Build.HARDWARE);
            result.isSandboxed = true;
        }

        if (new File("/storage/emulated/10/Android").exists()) {
            result.sandboxTraces.add("检测到工作资料/沙盒环境 (用户10)");
            result.isSandboxed = true;
        }
    }

    // ==================== 安全检测 ====================
    private static final int WEIGHT_BOOTLOADER = 25;
    private static final int WEIGHT_SELINUX = 20;
    private static final int WEIGHT_ROOT_TRACES = 25;
    private static final int WEIGHT_DEBUGGABLE = 15;
    private static final int WEIGHT_SYSTEM_RW = 10;
    private static final int WEIGHT_EMULATOR = 5;

    public static int getRunningProcessCount(Context context) {
        int count = 0;
        File procDir = new File("/proc");
        File[] files = procDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory() && f.getName().matches("\\d+")) {
                    count++;
                }
            }
        }
        return count;
    }

    public static SniffResult sniff(Context context) {
        SniffResult result = new SniffResult();
        result.totalScore = 100;
        result.kernelVersion = getKernelVersion();
        result.securityPatch = Build.VERSION.SECURITY_PATCH;
        result.buildFingerprint = Build.FINGERPRINT;

        result.storageUserType = getStorageUserType(result);

        detectDeveloperOptions(context, result);
        detectRuntimeAndHookFrameworks(context, result);
        detectPartitionMountStatus(result);
        detectAvbAndDmVerity(result);
        detectSelinuxPolicyTraces(result);
        detectSandbox(result);

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

        result.isEmulator = isEmulator() || result.isSandboxed;
        if (result.isEmulator) {
            result.totalScore -= WEIGHT_EMULATOR;
            result.details.add("⚠️ 模拟器/沙盒环境");
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
        String[] paths = {
                "/system/bin/su", "/system/xbin/su", "/sbin/su", "/data/local/su",
                "/system/app/SuperSU.apk", "/system/app/Kinguser.apk",
                "/sbin/magisk", "/sbin/magiskpolicy", "/sbin/magiskhide"
        };
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