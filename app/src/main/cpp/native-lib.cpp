#include <jni.h>
#include <string>
#include <unistd.h>
#include <errno.h>
#include <signal.h>
#include <android/log.h>
#include "root_shell.h"
#include "include/exploits.h"
#include <sys/system_properties.h> // 添加这个头文件用于获取系统属性

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
int cve_2025_54642(void);
int cve_2023_20938(void);
int cve_2022_22057(void);
int cve_2025_20801(void);
int cve_2024_43066(void);
int cve_2025_48543(void);
int cve_2025_21479(void);
int cve_2025_36920(void);
int cve_2026_0038(void);
int cve_2026_0032(void);
int cve_2024_53104(void);
int cve_2025_0088(void);
int cve_2025_21479_with_phys(uint64_t pt_phys);

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
extern "C" JNIEXPORT jboolean JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_isHarmonyOS(JNIEnv *env, jclass clazz) {
    char sdk_version[PROP_VALUE_MAX] = {0};
    __system_property_get("ro.huawei.build.version.sdk", sdk_version);
    if (strlen(sdk_version) > 0) {
        return JNI_TRUE;
    }
    char emui_version[PROP_VALUE_MAX] = {0};
    __system_property_get("ro.build.version.emui", emui_version);
    if (strlen(emui_version) > 0) {
        return JNI_TRUE;
    }
    char harmony_os[PROP_VALUE_MAX] = {0};
    __system_property_get("ro.build.version.harmonyos", harmony_os);
    return (strlen(harmony_os) > 0) ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_cve20254642_1exploit(JNIEnv *env, jclass clazz) {
    LOGI("Calling cve_2025_54642()");
    return cve_2025_54642() == 0 ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_cve202320938_1exploit(JNIEnv *env, jclass clazz) {
    LOGI("Calling cve_2023_20938()");
    return cve_2023_20938() == 0 ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_cve202222057_1exploit(JNIEnv *env, jclass clazz) {
    LOGI("Calling cve_2022_22057()");
    return cve_2022_22057() == 0 ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_cve202520801_1exploit(JNIEnv *env, jclass clazz) {
    LOGI("Calling cve_2025_20801()");
    return cve_2025_20801() == 0 ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_cve202443066_1exploit(JNIEnv *env, jclass clazz) {
    LOGI("Calling cve_2024_43066()");
    return cve_2024_43066() == 0 ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_cve202548543_1exploit(JNIEnv *env, jclass clazz) {
    LOGI("Calling cve_2025_48543()");
    return cve_2025_48543() == 0 ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_cve202521479_1exploit(JNIEnv *env, jclass clazz) {
    LOGI("Calling cve_2025_21479()");
    return cve_2025_21479() == 0 ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_cve202536920_1exploit(JNIEnv *env, jclass clazz) {
    LOGI("Calling cve_2025_36920()");
    return cve_2025_36920() == 0 ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_cve20260038_1exploit(JNIEnv *env, jclass clazz) {
    LOGI("Calling cve_2026_0038()");
    return cve_2026_0038() == 0 ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_cve20260032_1exploit(JNIEnv *env, jclass clazz) {
    LOGI("Calling cve_2026_0032()");
    return cve_2026_0032() == 0 ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_JNIInterface_cve202453104_1exploit(JNIEnv *env, jclass clazz) {
    LOGI("Calling cve_2024_53104()");
    return cve_2024_53104() == 0 ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_exploits_CVE_12025_121479_1Chain_exploitWithPhys(
        JNIEnv *env, jobject thiz, jlong ptPhys) {
    // 调用 cve_2025_21479_with_phys
    return cve_2025_21479_with_phys((uint64_t)ptPhys) == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_dpdns_sqxy090123_kit_vulnkit_exploits_CVE_12025_121479_1Chain_getDirectBufferAddress(
        JNIEnv *env, jobject thiz, jobject buffer) {
    return (jlong) env->GetDirectBufferAddress(buffer);
}