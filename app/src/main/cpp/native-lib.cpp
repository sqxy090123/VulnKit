#include <jni.h>
#include <string>
#include <android/log.h>
#include "root_shell.h"
#include "exploits.h"

#define TAG "VulnKitNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jint JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_runExploit(JNIEnv *env, jclass clazz, jstring exploitName) {
    const char *name = env->GetStringUTFChars(exploitName, nullptr);
    LOGI("Running exploit: %s", name);

    int result = -1;
    if (strcmp(name, "CVE-2019-2215 (binder)") == 0) {
        result = cve_2019_2215();
    } else if (strcmp(name, "CVE-2020-0041 (binder)") == 0 ||
               strcmp(name, "CVE-2021-0920 (syscall)") == 0 ||
               strcmp(name, "CVE-2022-20411 (Qualcomm)") == 0 ||
               strcmp(name, "CVE-2023-20938 (kernel)") == 0) {
        LOGI("Exploit %s not implemented in current build", name);
        result = -1;
    }

    env->ReleaseStringUTFChars(exploitName, name);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_startRootShell(JNIEnv *env, jclass clazz) {
if (!is_root_shell_available()) {
establish_root_shell();
}
}

extern "C" JNIEXPORT jint JNICALL
        Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_writeRootShell(JNIEnv *env, jclass clazz, jstring cmd) {
const char *c_cmd = env->GetStringUTFChars(cmd, nullptr);
int ret = write_root_shell(c_cmd);
env->ReleaseStringUTFChars(cmd, c_cmd);
return ret;
}

extern "C" JNIEXPORT jstring JNICALL
        Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_readRootShell(JNIEnv *env, jclass clazz) {
char *line = read_root_shell();
if (line != NULL) {
return env->NewStringUTF(line);
}
return env->NewStringUTF("");
}

extern "C" JNIEXPORT void JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_closeRootShell(JNIEnv *env, jclass clazz) {
close_root_shell();
}

extern "C" JNIEXPORT jboolean JNICALL
        Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_isRootShellAvailable(JNIEnv *env, jclass clazz) {
return is_root_shell_available() ? JNI_TRUE : JNI_FALSE;
}