package org.dpdns.sqxy090123.kit.vulnkit;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public class TerminalView extends ScrollView {
    private TextView outputView;
    private EditText inputEdit;
    private LinearLayout container;
    private Process shellProcess;
    private DataOutputStream shellOutput;
    private BufferedReader shellInput;
    private boolean usingRootShell = false;

    public TerminalView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // 设置背景
        setBackgroundColor(Color.BLACK);

        // 创建垂直布局容器（ScrollView 只能有一个直接子视图）
        container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(container);

        // 输出文本区域
        outputView = new TextView(getContext());
        outputView.setTextColor(Color.GREEN);
        outputView.setTextSize(12);
        outputView.setPadding(16, 16, 16, 16);
        outputView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        container.addView(outputView);

        // 输入框
        inputEdit = new EditText(getContext());
        inputEdit.setTextColor(Color.GREEN);
        inputEdit.setBackgroundColor(Color.BLACK);
        inputEdit.setHint("$ ");
        inputEdit.setHintTextColor(Color.DKGRAY);
        inputEdit.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        inputEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                executeCommand(v.getText().toString());
                v.setText("");
                return true;
            }
            return false;
        });
        container.addView(inputEdit);
    }

    public void startShell() {
        try {
            shellProcess = Runtime.getRuntime().exec("sh");
            shellOutput = new DataOutputStream(shellProcess.getOutputStream());
            shellInput = new BufferedReader(new InputStreamReader(shellProcess.getInputStream()));
            new Thread(() -> {
                try {
                    String line;
                    while ((line = shellInput.readLine()) != null) {
                        final String finalLine = line;
                        post(() -> outputView.append(finalLine + "\n"));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    post(() -> outputView.append(e + "\n"));
                }
            }).start();
        } catch (Exception e) {
            outputView.append("启动 shell 失败: " + e.getMessage() + "\n");
        }
    }

    private void executeCommand(String cmd) {
        if (cmd.equals("su")) {
            if (JNIInterface.isRootShellAvailable()) {
                switchToRootShell();
            } else {
                outputView.append("Root shell 不可用，请先利用提权漏洞\n");
            }
            return;
        }
        if (cmd.startsWith("runin ")) {
            String target = cmd.substring(6).trim(); // 提取 app|shell|system|root
            if (target.equals("app")) {
                // 降级到普通用户 shell (启动一个新的进程)
                // 注意：这需要 JNIInterface 实现对应的降级逻辑，目前先留空
                outputView.append("切换到 App shell 的功能正在开发中\n");
            } else if (target.equals("shell")) {
                // 降级到 shell 用户 (通常是 2000 或 2001)
                outputView.append("切换到 Shell shell 的功能正在开发中\n");
            } else if (target.equals("system")) {
                // 降级到 system 用户
                outputView.append("切换到 System shell 的功能正在开发中\n");
            } else if (target.equals("root")) {
                if (!usingRootShell) {
                    switchToRootShell();
                } else {
                    outputView.append("已经是 root shell\n");
                }
            } else {
                outputView.append("用法: runin app|shell|system|root\n");
            }
            return;
        }

        // 处理 'exituser' 命令，退出当前 shell，返回初始 shell
        if (cmd.equals("exituser")) {
            if (usingRootShell) {
                // 关闭当前 root shell
                JNIInterface.closeRootShell();
                usingRootShell = false;
                // 重新启动普通 shell
                startShell();
                outputView.append("已退出 root shell，返回普通 shell\n");
            } else {
                outputView.append("当前不是 root shell，无需退出\n");
            }
            return;
        }

        if (usingRootShell) {
            JNIInterface.writeRootShell(cmd + "\n");
            String output = JNIInterface.readRootShell();
            outputView.append((output != null && !output.isEmpty()) ? output + "\n" : "(命令无输出)\n");
        } else {
            if (shellOutput == null) {
                outputView.append("shell 未启动\n");
                return;
            }
            try {
                shellOutput.writeBytes(cmd + "\n");
                shellOutput.flush();

                // 读取标准输出和错误输出
                java.io.InputStream is = shellProcess.getInputStream();
                java.io.InputStream es = shellProcess.getErrorStream();
                byte[] buf = new byte[4096];
                int len;
                while ((len = is.read(buf)) > 0) {
                    outputView.append(new String(buf, 0, len));
                }
                while ((len = es.read(buf)) > 0) {
                    outputView.append("[ERR] " + new String(buf, 0, len));
                }
            } catch (Exception e) {
                outputView.append("命令执行失败: " + e.getMessage() + "\n");
            }
        }
    }

    private void switchToRootShell() {
        // 关闭普通 shell
        if (shellProcess != null) {
            shellProcess.destroy();
            shellProcess = null;
        }
        usingRootShell = true;
        inputEdit.setHint("# ");
        outputView.append("切换到 root shell，您现在拥有 root 权限\n");
        // 启动一个读取线程，持续输出 root shell 的结果
        new Thread(() -> {
            while (usingRootShell && JNIInterface.isRootShellAvailable()) {
                String line = JNIInterface.readRootShell();
                if (line != null && !line.isEmpty()) {
                    final String finalLine = line;
                    post(() -> outputView.append(finalLine + "\n"));
                } else {
                    try { Thread.sleep(100); } catch (InterruptedException e) { break; }
                }
            }
        }).start();
        if (usingRootShell) {
            // 自动执行 whoami 验证
            JNIInterface.writeRootShell("whoami\n");
            String output = JNIInterface.readRootShell();
            if (output != null && output.contains("root")) {
                outputView.append("Root shell 验证成功: " + output);
            } else {
                outputView.append("Root shell 验证失败，输出: " + (output != null ? output : "null") + "\n");
                outputView.append("请检查漏洞是否正确提权\n");
                // 如果验证失败，可以考虑回退到普通 shell
                usingRootShell = false;
                startShell();
            }
        }
    }
}