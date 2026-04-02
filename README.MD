# VulnKit - Android 漏洞利用工具集

## 项目简介
VulnKit 是一个用于 Android 设备本地提权和安全研究的工具集。它集成了多个历史高危漏洞的利用代码，支持通过反射动态加载漏洞模块，并提供统一的 Root Shell 管理接口。

⚠️ **重要警告**：本项目仅用于授权的安全研究和教育目的。未经授权使用漏洞利用代码攻击他人设备是违法行为。

## 功能特性
- 支持多个 CVE 漏洞（CVE-2019-2215, CVE-2024-0044, CVE-2025-38352 等）
- 反射动态加载漏洞类，便于扩展和维护
- 统一的 Root Shell 管理（基于管道通信）
- 终端界面支持命令交互
- 漏洞列表可搜索、过滤

## 已集成漏洞列表
| 漏洞编号 | 影响版本 | 类型 |
|---------|---------|------|
| CVE-2019-2215 | Android 9-10 | 提权 |
| CVE-2020-29374 | Android 10-11 | 内核提权 |
| CVE-2022-20347 | Android 10-12L | 蓝牙权限绕过 |
| CVE-2022-20360 | Android 10-12L | Secure NFC 提权 |
| CVE-2023-20913 | Android 10-13 | 点击劫持 |
| CVE-2024-0044 | Android 12-13 | run-as 注入 |
| CVE-2024-43093 | Android 12-15 | Unicode 路径遍历 |
| CVE-2025-38352 | Android 13-16 | POSIX CPU Timer UAF |
| CVE-2025-48572 | Android 13-16 | Framework 提权 |
| CVE-2026-0107 | Android 10+ | 内存控制器提权 |
| CVE-2026-21385 | Android 13-16 | Qualcomm KGSL UAF |

## 构建与安装
### 环境要求
- Android Studio Hedgehog 或更高版本
- NDK r25c 或更高
- Android SDK 33

### 编译步骤
1. 克隆仓库
2. 在 Android Studio 中打开项目
3. 连接 Android 设备（已开启 USB 调试）
4. 点击 Run 按钮

### 手动编译 Native 库
```bash
cd app/src/main/cpp
ndk-build
```

## 使用方法
1. 安装 APK 并授予必要权限（存储、悬浮窗等）
2. 打开应用，进入漏洞列表（点击右上角 "+"）
3. 选择对应漏洞，点击“继续”执行利用
4. 成功后返回主界面，在终端中输入 `su` 切换至 root shell

## 第三方库依赖
- Shizuku API (v13.1.5) - 用于辅助权限操作
- AndroidX AppCompat / RecyclerView

## 注意事项
- 部分漏洞可能导致系统崩溃或重启，请提前备份数据
- CVE-2024-31317 已因稳定性问题移除
- 持久化功能已禁用，避免设备变砖

## 贡献指南
欢迎提交 Issue 或 Pull Request。新增漏洞时请实现 `IExploit` 接口，并在 `ExploitLoader` 中注册类名。

## 许可证
本项目仅供学习研究，请勿用于非法用途。