#include <jni.h>
#include <string>
#include <unistd.h>
#include <errno.h>
#include <signal.h>
#include <android/log.h>
#include "root_shell.h"
#include "exploits.h"

#define TAG "VulnKitNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {
int cve_2019_2215(void);
int cve_2020_0041(void);
int cve_2021_0920(void);
bool cve_2025_38352(void);
bool cve_2026_0107(void);
bool cve_2026_21385(void);
}

// 检测 setuid 是否被 seccomp 拦截
extern "C" JNIEXPORT jint JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_checkSetuidAvailable(JNIEnv *env, jclass clazz) {
    uid_t uid = getuid();
    int ret = setuid(uid);
    if (ret == -1 && errno == EPERM) {
        return 1;  // 可用但无权限（需要提权后使用）
    } else if (ret == 0) {
        return 2;  // 已经是 root
    }
    return 0; // 被 seccomp 拦截或其他错误
}

// 各漏洞 JNI 实现（函数名已修正下划线转义）
extern "C" JNIEXPORT jint JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_cve20192215(JNIEnv *env, jclass clazz) {
    LOGI("Calling cve_2019_2215()");
    return cve_2019_2215();
}

extern "C" JNIEXPORT jint JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_cve20200041(JNIEnv *env, jclass clazz) {
    LOGI("cve_2020_0041 not implemented");
    return -1;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_cve20210920(JNIEnv *env, jclass clazz) {
    LOGI("cve_2021_0920 not implemented");
    return -1;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_cve202538352_1exploit(JNIEnv *env, jclass clazz) {
    LOGI("Calling cve_2025_38352()");
    return cve_2025_38352() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_cve20260107_1exploit(JNIEnv *env, jclass clazz) {
    LOGI("Calling cve_2026_0107()");
    return cve_2026_0107() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_cve202621385_1exploit(JNIEnv *env, jclass clazz) {
    LOGI("Calling cve_2026_21385()");
    return cve_2026_21385() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_cve202443093_1setuidZero(JNIEnv *env, jclass clazz) {
    int ret = setuid(0);
    LOGI("setuid(0) returned %d, errno=%d", ret, errno);
    return (ret == 0 && getuid() == 0) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_cve202548572_1setuidZero(JNIEnv *env, jclass clazz) {
    return Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_cve202443093_1setuidZero(env, clazz);
}

// Root Shell 管理
extern "C" JNIEXPORT jint JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_startRootShell(JNIEnv *env, jclass clazz) {
    return establish_root_shell();
}

extern "C" JNIEXPORT jint JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_writeRootShell(JNIEnv *env, jclass clazz, jstring cmd) {
    const char *c_cmd = env->GetStringUTFChars(cmd, nullptr);
    if (!c_cmd) return -1;
    char *buf = (char*)malloc(strlen(c_cmd) + 2);
    sprintf(buf, "%s\n", c_cmd);
    int ret = write_root_shell(buf);
    free(buf);
    env->ReleaseStringUTFChars(cmd, c_cmd);
    return ret;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_readRootShell(JNIEnv *env, jclass clazz) {
    char *line = read_root_shell();
    if (line) return env->NewStringUTF(line);
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_closeRootShell(JNIEnv *env, jclass clazz) {
    close_root_shell();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_isRootShellAvailable(JNIEnv *env, jclass clazz) {
    return is_root_shell_available() ? JNI_TRUE : JNI_FALSE;
}