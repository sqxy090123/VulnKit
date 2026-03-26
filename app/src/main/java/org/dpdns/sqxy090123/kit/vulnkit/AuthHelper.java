package org.dpdns.sqxy090123.kit.vulnkit;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;
import android.widget.EditText;
import androidx.annotation.RequiresApi;

public class AuthHelper {
    private Context context;
    private CancellationSignal cancellationSignal;
    private BiometricPrompt biometricPrompt;

    public interface AuthCallback {
        void onSuccess();

        void onFailure();
    }

    public AuthHelper(Context context) {
        this.context = context;
    }

    public void authenticate(AuthCallback callback) {
        // 强制使用密码验证，确保稳定
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("验证身份");
        builder.setMessage("请输入密码以使用漏洞工具");
        final EditText input = new EditText(context);
        builder.setView(input);
        builder.setPositiveButton("确定", (dialog, which) -> {
            String password = input.getText().toString();
            if ("vulnkit123".equals(password)) {
                callback.onSuccess();
            } else {
                callback.onFailure();
            }
        });
        builder.setNegativeButton("取消", (dialog, which) -> callback.onFailure());
        builder.show();
    }
}