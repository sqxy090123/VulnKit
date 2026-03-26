package org.dpdns.sqxy090123.kit.vulnkit;

public class JNIInterface {
    static {
        System.loadLibrary("vulnkit");
    }

    // 尝试利用指定漏洞，成功返回 0，失败返回 -1
    public static native int runExploit(String exploitName);

    // 利用成功后，启动 root shell（阻塞，直到 shell 准备好）
    public static native void startRootShell();

    // 向 root shell 写入命令，返回写入的字节数
    public static native int writeRootShell(String cmd);

    // 从 root shell 读取一行输出（阻塞），返回读取的字符串
    public static native String readRootShell();

    // 关闭 root shell
    public static native void closeRootShell();

    // 检查 root shell 是否可用
    public static native boolean isRootShellAvailable();
}