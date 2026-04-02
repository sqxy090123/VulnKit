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
}