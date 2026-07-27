package com.fongmi.android.tv.utils;

import android.content.Context;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import java.util.concurrent.Executor;

public class Biometric {
    public static void authenticate(FragmentActivity activity, Runnable onSuccess) {
        Executor executor = ContextCompat.getMainExecutor(activity);
        BiometricPrompt.AuthenticationCallback callback = new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                if (onSuccess != null) onSuccess.run();
            }
        };
        BiometricPrompt prompt = new BiometricPrompt(activity, executor, callback);
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("验证身份").setNegativeButtonText("取消").build();
        prompt.authenticate(info);
    }
}
