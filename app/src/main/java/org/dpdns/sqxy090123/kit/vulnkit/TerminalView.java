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
                }
            }).start();
        } catch (Exception e) {
            outputView.append("启动 shell 失败: " + e.getMessage() + "\n");
        }
    }

    private void executeCommand(String cmd) {
        // 特殊命令：切换到 root shell
        if (cmd.equals("su")) {
            if (JNIInterface.isRootShellAvailable()) {
                switchToRootShell();
            } else {
                outputView.append("Root shell 不可用，请先利用漏洞\n");
            }
            return;
        }

        if (usingRootShell) {
            // 使用 root shell 执行命令
            JNIInterface.writeRootShell(cmd + "\n");
            String output = JNIInterface.readRootShell();
            outputView.append(output + "\n");
        } else {
            if (shellOutput == null) {
                outputView.append("shell 未启动\n");
                return;
            }
            try {
                shellOutput.writeBytes(cmd + "\n");
                shellOutput.flush();
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
    }
}