package org.dpdns.sqxy090123.kit.vulnkit;

import android.util.Log;

public class JNIInterface {
    static {
        System.loadLibrary("vulnkit");
    }

    // 现有 native 方法声明（全部保留）
    public static native int checkSetuidAvailable();
    public static native int cve20192215();
    public static native int cve20200041();
    public static native int cve20210920();
    public static native boolean cve202538352_exploit();
    public static native boolean cve20260107_exploit();
    public static native boolean cve202621385_exploit();
    public static native boolean cve202443093_setuidZero();
    public static native boolean cve202548572_setuidZero();
    public static native int startRootShell();
    public static native int writeRootShell(String cmd);
    public static native String readRootShell();
    public static native void closeRootShell();
    public static native boolean isRootShellAvailable();
    public static native boolean isHarmonyOS();
    public static native boolean cve20254642_exploit();
    public static native boolean cve202320938_exploit();
    public static native boolean cve202222057_exploit();
    public static native boolean cve202520801_exploit();
    public static native boolean cve202443066_exploit();
    public static native boolean cve202548543_exploit();
    public static native boolean cve202521479_exploit();   // 物理内存穷举漏洞
    public static native boolean cve202536920_exploit();
    public static native boolean cve20260038_exploit();
    public static native boolean cve20260032_exploit();
    public static native boolean cve202453104_exploit();
    public static native boolean cve20250088_exploit_bruteforce(long[] resultParams);
    public static native boolean cve20250088_exploit_with_params(long commit_creds, long prepare_kernel_cred, int offset);

    /**
     * 兼容旧调用，直接执行 CVE-2025-21479 漏洞（物理内存穷举）
     * @return 漏洞利用是否成功
     */
    public static boolean tryShizukuOrExploit() {
        Log.i("JNIInterface", "Directly calling CVE-2025-21479 exploit (no Shizuku)");
        return cve202521479_exploit();
    }
}