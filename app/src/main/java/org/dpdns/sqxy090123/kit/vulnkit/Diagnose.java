package org.dpdns.sqxy090123.kit.vulnkit;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.util.LinkedList;
import java.util.Locale;

/**
 * 系统健康诊断引擎
 * 基于内核性能分析经验，将底层指标转换为评分，并实现内存颠簸检测。
 * 参考 OPPO ColorOS 13 深度分析模型。
 */
public class Diagnose {
    private static final String TAG = "Diagnose";

    public enum Level {
        HEALTHY,   // 健康
        WARNING,   // 轻度压力
        CRITICAL   // 严重卡顿/颠簸
    }

    public static class DiagnoseResult {
        public Level level;
        public int totalScore;
        public int maxScore = 100;
        public String summary;
        public StringBuilder details = new StringBuilder();

        // 因子评分明细
        public int memoryScore;
        public int zramScore;
        public int cpuScore;
        public int processScore;
        public int fragmentScore;
        public boolean thrashingDetected;
        public float memVolatility; // 内存波动率
    }

    // 权重配置 (总和 100)
    private static final int WEIGHT_MEMORY = 30;
    private static final int WEIGHT_ZRAM = 25;
    private static final int WEIGHT_CPU = 20;
    private static final int WEIGHT_PROCESS = 15;
    private static final int WEIGHT_FRAGMENT = 10;

    // 内存波动率检测
    private static final int SAMPLE_INTERVAL_MS = 5000;   // 5 秒
    private static final int SAMPLE_HISTORY_SIZE = 12;    // 1 分钟
    private final LinkedList<Long> availMemHistory = new LinkedList<>();
    private long totalRam = 0;
    private HandlerThread samplerThread;
    private Handler samplerHandler;
    private Context appContext;
    private volatile boolean sampling = false;

    public Diagnose(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * 启动内存波动率采样 (后台线程)
     */
    public void startSampling() {
        if (sampling) return;
        sampling = true;
        samplerThread = new HandlerThread("MemSampler");
        samplerThread.start();
        samplerHandler = new Handler(samplerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (!sampling) return;
                EnvironmentSniffer.MemInfo mi = EnvironmentSniffer.getMemInfo();
                totalRam = mi.totalRam;
                synchronized (availMemHistory) {
                    availMemHistory.addLast(mi.availRam);
                    if (availMemHistory.size() > SAMPLE_HISTORY_SIZE)
                        availMemHistory.removeFirst();
                }
                sendEmptyMessageDelayed(0, SAMPLE_INTERVAL_MS);
            }
        };
        samplerHandler.sendEmptyMessage(0);
    }

    public void stopSampling() {
        sampling = false;
        if (samplerHandler != null) {
            samplerHandler.removeCallbacksAndMessages(null);
        }
        if (samplerThread != null) {
            samplerThread.quitSafely();
        }
    }

    /**
     * 执行一次完整诊断 (包含当前即时数据)
     */
    public DiagnoseResult diagnose() {
        DiagnoseResult result = new DiagnoseResult();
        result.totalScore = 100;

        // 采集基础数据
        EnvironmentSniffer.MemInfo mem = EnvironmentSniffer.getMemInfo();
        float[] load = EnvironmentSniffer.getCpuLoad();
        int cpuCores = EnvironmentSniffer.getCpuCoreCount();
        int procCount = EnvironmentSniffer.getRunningProcessCount(appContext);
        long uptime = EnvironmentSniffer.getSystemUptime();
        EnvironmentSniffer.StorageStatus storage = EnvironmentSniffer.getStorageStatus();

        // 1. 内存压力评分 (可用内存占比)
        float availRatio = mem.getAvailRatio();
        int memScore = 100;
        if (availRatio < 0.15f) memScore = 30;
        else if (availRatio < 0.25f) memScore = 60;
        else if (availRatio < 0.35f) memScore = 80;
        result.memoryScore = memScore;
        result.totalScore -= (100 - memScore) * WEIGHT_MEMORY / 100;

        // 2. ZRAM 使用率评分 (反映压缩内存压力)
        float zramRatio = mem.getZramUsageRatio();
        int zramScore = 100;
        if (zramRatio > 0.6f) zramScore = 30;
        else if (zramRatio > 0.3f) zramScore = 60;
        else if (zramRatio > 0.15f) zramScore = 80;
        result.zramScore = zramScore;
        result.totalScore -= (100 - zramScore) * WEIGHT_ZRAM / 100;

        // 3. CPU 负载评分
        float load1 = load[0];
        boolean cpuDataValid = (load1 > 0.01f);
        float loadPerCore = load1 / cpuCores;
        int cpuScore = 100;
        if (cpuDataValid) {
            if (loadPerCore > 1.5f) cpuScore = 30;
            else if (loadPerCore > 1.0f) cpuScore = 60;
            else if (loadPerCore > 0.7f) cpuScore = 80;
        }
        result.cpuScore = cpuScore;
        result.totalScore -= (100 - cpuScore) * WEIGHT_CPU / 100;

        // 4. 后台进程数评分
        int procScore = 100;
        if (procCount > 250) procScore = 30;
        else if (procCount > 150) procScore = 60;
        else if (procCount > 100) procScore = 80;
        result.processScore = procScore;
        result.totalScore -= (100 - procScore) * WEIGHT_PROCESS / 100;

        // 5. 碎片度评分 (基于开机时长 + ZRAM 压力推断)
        int fragScore = 100;
        long uptimeDays = uptime / (1000 * 3600 * 24);
        if (uptimeDays > 7 && zramRatio > 0.5f) fragScore = 50;
        else if (uptimeDays > 3 && zramRatio > 0.3f) fragScore = 75;
        result.fragmentScore = fragScore;
        result.totalScore -= (100 - fragScore) * WEIGHT_FRAGMENT / 100;

        // 6. 内存颠簸检测 (基于波动率)
        result.thrashingDetected = false;
        result.memVolatility = 0f;
        synchronized (availMemHistory) {
            if (availMemHistory.size() >= SAMPLE_HISTORY_SIZE) {
                result.memVolatility = computeVolatility();
                // 条件：波动率 > 0.3 且 当前可用内存 < 20% 总内存
                if (result.memVolatility > 0.3f && availRatio < 0.20f) {
                    result.thrashingDetected = true;
                }
            }
        }

        // 综合等级判定 (颠簸直接 CRITICAL)
        if (result.thrashingDetected) {
            result.level = Level.CRITICAL;
            result.summary = "内存颠簸 (工作集剧烈抖动)";
        } else if (result.totalScore < 40) {
            result.level = Level.CRITICAL;
            result.summary = "系统资源严重不足";
        } else if (result.totalScore < 70) {
            result.level = Level.WARNING;
            result.summary = "系统轻度压力，建议清理后台";
        } else {
            result.level = Level.HEALTHY;
            result.summary = "系统运行流畅";
        }

        // 生成详细报告
        buildDetails(result, mem, load, cpuCores, procCount, uptime, storage);

        return result;
    }

    /**
     * 计算内存波动率 (标准差 / 平均值)
     */
    private float computeVolatility() {
        if (availMemHistory.isEmpty()) return 0f;
        double sum = 0;
        for (long v : availMemHistory) sum += v;
        double mean = sum / availMemHistory.size();
        double sqDiff = 0;
        for (long v : availMemHistory) sqDiff += Math.pow(v - mean, 2);
        double std = Math.sqrt(sqDiff / availMemHistory.size());
        return mean > 0 ? (float) (std / mean) : 0f;
    }

    private void buildDetails(DiagnoseResult r, EnvironmentSniffer.MemInfo mem,
                              float[] load, int cores, int procs, long uptime,
                              EnvironmentSniffer.StorageStatus storage) {
        r.details.append("=== 诊断报告 ===\n");
        r.details.append(String.format(Locale.US,
                "内存: 可用 %.1f%% (%.1f MB / %.1f MB)\n",
                mem.getAvailRatio()*100, mem.availRam/1024f, mem.totalRam/1024f));
        r.details.append(String.format("ZRAM 使用: %.1f%% (%.1f MB / %.1f MB)\n",
                mem.getZramUsageRatio()*100, mem.getZramUsed()/1024f, mem.swapTotal/1024f));
        r.details.append(String.format("CPU 负载 (1m/5m/15m): %.2f / %.2f / %.2f (核心数: %d)\n",
                load[0], load[1], load[2], cores));
        r.details.append(String.format("运行进程数: %d\n", procs));
        r.details.append(String.format("开机时长: %d 天 %d 小时\n",
                uptime / (1000*3600*24), (uptime / (1000*3600)) % 24));
        r.details.append(String.format("存储: 可用 %.1f GB / 总 %.1f GB\n",
                storage.freeBytes/1073741824f, storage.totalBytes/1073741824f));

        r.details.append("\n--- 评分明细 ---\n");
        r.details.append(String.format("内存得分: %d/100 (权重 %d%%)\n", r.memoryScore, WEIGHT_MEMORY));
        r.details.append(String.format("ZRAM得分: %d/100 (权重 %d%%)\n", r.zramScore, WEIGHT_ZRAM));
        r.details.append(String.format("CPU得分: %d/100 (权重 %d%%)\n", r.cpuScore, WEIGHT_CPU));
        r.details.append(String.format("进程得分: %d/100 (权重 %d%%)\n", r.processScore, WEIGHT_PROCESS));
        r.details.append(String.format("碎片得分: %d/100 (权重 %d%%)\n", r.fragmentScore, WEIGHT_FRAGMENT));
        r.details.append(String.format("总分: %d / %d\n", r.totalScore, r.maxScore));

        r.details.append("\n--- 高级指标 ---\n");
        r.details.append(String.format("内存波动率: %.2f (阈值 0.3)\n", r.memVolatility));
        r.details.append(String.format("颠簸检测: %s\n", r.thrashingDetected ? "是" : "否"));
        r.details.append(String.format("诊断结论: %s - %s\n", r.level, r.summary));

    }
}