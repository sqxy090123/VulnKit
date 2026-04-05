# VulnKit - Android 漏洞利用框架

VulnKit 是一个模块化、可扩展的 Android 本地权限提升与安全漏洞验证工具。它集成了多个历史高危漏洞的利用代码，支持通过反射动态加载漏洞模块，并提供统一的 Root Shell 管理接口。

⚠️ **重要警告**：本项目仅用于授权的安全研究和教育目的。未经授权使用漏洞利用代码攻击他人设备是违法行为。部分漏洞利用可能导致系统不稳定或重启，请在测试前备份数据。

## 功能特性
- 支持多个 CVE 漏洞（提权、越权、信息泄露等）
- 反射动态加载漏洞类，便于扩展和维护
- 统一的 Root Shell 管理（基于管道通信）
- 终端界面支持命令交互
- 漏洞列表可搜索、过滤
- 线程池管理 + 超时控制，避免单个漏洞崩溃影响整体
- 预检系统调用可用性（如 setuid 被 seccomp 拦截时自动跳过）

## 已集成漏洞列表
根据项目实际代码和文档，目前已集成的 CVE 漏洞共 **16** 个。下表按 CVE 年份（从旧到新）排序，与 README.md 表格格式一致:s
| CVE 编号 | 漏洞简述 | 影响 Android 版本 | 漏洞类型 |
|----------|----------|-------------------|----------|
| CVE-2019-2215 | Binder 驱动 UAF，覆盖 addr_limit 实现内核提权 | 9–10 | 内核提权 (Root) |
| CVE-2020-29374 | Linux 内核 UAF 漏洞 | 10–11 | 内核提权 (Root) |
| CVE-2021-0340 | NFC 组件泄露设备位置信息 | 10 | 信息泄露 (User) |
| CVE-2022-20347 | 蓝牙权限校验绕过 | 10–12L | 越权提权 (User) |
| CVE-2022-20360 | Secure NFC 权限提升 | 10–12L | 越权提权 (User) |
| CVE-2023-20913 | UI 点击劫持漏洞 | 10–13 | 越权提权 (User) |
| CVE-2024-0044 | run-as 命令注入提权 | 12–13 | 越权提权 (User) |
| CVE-2024-43093 | Unicode 路径遍历信息泄露 | 12–15 | 信息泄露 (User) |
| CVE-2025-12345 | Wi-Fi 配置越权临时修改 | 10+ | 越权提权 (User) |
| CVE-2025-32320 | 剪贴板历史泄露 | 13+ | 信息泄露 (User) |
| CVE-2025-38352 | POSIX CPU 定时器 UAF 竞争条件 | 13–16 | 内核提权 (Root) |
| CVE-2025-48536 | 混淆代理漏洞导致系统设置篡改 | 13–16 | 越权提权 (User) |
| CVE-2025-48572 | Android Framework 组件提权 | 13–16 | 越权提权 (User) |
| CVE-2025-67890 | 音量设置越权修改 | 11+ | 越权提权 (User) |
| CVE-2026-0107 | 图形内存控制器 confused deputy 提权 | 10+ | 内核提权 (Root) |
| CVE-2026-21385 | Qualcomm KGSL 显示组件内存损坏 UAF | 13–16 | 内核提权 (Root) |

> 注：CVE-2025-12345 与 CVE-2025-67890 为项目预留/演示编号，对应实际漏洞（Wi-Fi 配置修改、音量设置修改）。所有 CVE 均已在对应 Android 版本环境验证通过。

## 环境要求
- Android Studio Hedgehog 或更高版本
- NDK r25c 或更高
- Android SDK 33 (targetSdk) / minSdk 25
- 测试设备：已解锁 Bootloader 或允许安装测试应用（无需 root）

## 编译与安装
### 克隆仓库
```bash
git clone https://github.com/your-repo/VulnKit.git
cd VulnKit
```

使用 Android Studio 打开项目

- 等待 Gradle 同步完成
- 连接 Android 设备（开启 USB 调试）
- 点击 Run 按钮

手动编译 Native 库

```bash
cd app/src/main/cpp
ndk-build
```

### 生成 APK

```bash
./gradlew assembleDebug
```

## 使用方法

1. 安装 APK 并授予必要权限（存储、悬浮窗等）
2. 打开应用，主界面为终端（可输入命令）
3. 点击右上角 "+" 进入漏洞列表
4. 选择对应漏洞，点击“继续”执行利用
5. 成功后返回主界面，在终端中输入 su 切换至 root shell（仅提权漏洞有效）

## 注意事项

- 部分漏洞可能导致系统崩溃或重启，请提前备份数据
- CVE-2024-31317 已因稳定性问题移除
- 持久化功能已禁用，避免设备变砖
- 在较新的 Android 内核上，某些系统调用（如 setuid）可能被 seccomp 拦截，应用会自动检测并跳过相应漏洞

## 第三方库依赖

- Shizuku API (v13.1.5) - 辅助权限操作（可选）
- AndroidX AppCompat / RecyclerView / Material Design

## 贡献指南

欢迎提交 Issue 或 Pull Request。新增漏洞时请实现 IExploit 接口，并在 ExploitLoader 中注册类名。

## 许可证

#本项目仅供学习研究，请勿用于非法用途。