<div align="center">
  <img width="160" height="160" src="assets/branding/xmplus.png" alt="XMPro">
  <h1>XMPro</h1>
  <p>喜马拉雅第三方收听客户端 · Kotlin + Jetpack Compose</p>

  [![Android CI](https://github.com/angusdevgo/XMPro/actions/workflows/android.yml/badge.svg)](https://github.com/angusdevgo/XMPro/actions/workflows/android.yml)
  [![GitHub release](https://img.shields.io/github/v/release/angusdevgo/XMPro)](https://github.com/angusdevgo/XMPro/releases/latest)
  [![GitHub downloads](https://img.shields.io/github/downloads/angusdevgo/XMPro/total)](https://github.com/angusdevgo/XMPro/releases)
  [![License](https://img.shields.io/badge/license-AGPL--3.0--or--later-blue)](LICENSE)
</div>

<br/>

## 这是什么

XMPro 是 [XMPlus](https://github.com/teikengstudio/XMPlus) 的**修改版（二改）**。

上游 XMPlus 用 Flutter 实现；本项目把客户端整体重写为纯 Kotlin + Jetpack Compose，
界面风格重做，移除下载功能与桌面端。

| | 上游 XMPlus | 本项目 XMPro |
| :--- | :--- | :--- |
| 实现 | Flutter / Dart | Kotlin + Jetpack Compose |
| 平台 | Android + Windows 桌面 | 仅 Android（arm64-v8a） |
| 下载与转码 | 有 | 已移除 |
| 界面风格 | 上游自有 | 重新设计 |

主要改动（2026-11）：

- 数据层、协议签名、后台播放、多账号整体用 Kotlin 重写，60 项单元测试随代码走。
- 底栏改为「主页 / 听书 / 我的」，我的页合并账号、听时、关注粉丝订阅收藏与各项设置。
- 首页频道可自定义排序与隐藏；专辑页支持选集区间，长连载（数千集）可直接跳到任意区段。
- 关注 / 粉丝列表可点进主播主页查看其专辑。
- 外观支持动态取色与 7 套预设主题；播放设置支持音质与跳过片头片尾。
- 播放进度云同步、本地备份与恢复（可选包含登录信息）。
- 网络签名时间戳统一按服务端时间校正，避免设备时间漂移导致听时查询被拒。
- 移除下载、转码、桌面端与相关原生工具。

本项目与喜马拉雅官方无关，仅供个人学习与自用；请使用自己的账号，并遵守服务条款。

## 下载

从 [Releases](https://github.com/angusdevgo/XMPro/releases) 下载：

```
XMPro-<版本>-android-arm64-v8a.apk
SHA256SUMS
```

仅构建 `arm64-v8a` 单 ABI，安装前请确认设备架构。

## 编译

工具链：JDK 17、Gradle 8.9、AGP 8.7.3、Kotlin 2.0.20；`compileSdk` 35、`minSdk` 24，仅 `arm64-v8a`。

```sh
./android/gradlew -p android :app:assembleDebug
./android/gradlew -p android :app:testDebugUnitTest
```

正式构建必须配置 JKS 签名环境变量，签名材料不得提交到仓库：

```sh
export ANDROID_KEYSTORE_PATH=/path/to/release.jks
export ANDROID_KEYSTORE_PASSWORD=...
export ANDROID_KEY_ALIAS=...
export ANDROID_KEY_PASSWORD=...
./android/gradlew -p android :app:assembleRelease
```

CI（`.github/workflows/android.yml`）在推送 `v*` 标签时用仓库 secrets 签名、校验证书与版本号，
并把产物发布到 Releases。`sdk.dir` 通过 `android/local.properties` 指定。

迁移前的 Flutter 实现见标签 `flutter-final`，仓库里只保留 Kotlin 工程。

## 许可

本项目以 **AGPL-3.0-or-later** 发布，见 [LICENSE](LICENSE)。

它等价于 GPL-3.0 的全部条款，另加第 13 条「网络提供」：若把修改版作为网络服务提供，
必须向使用者提供对应源码。若你分发本项目或其修改版，须保留同样的许可并提供源码。

上游与第三方组件的声明保留在 [NOTICE](NOTICE) 中，包括：

- [teikengstudio/XMPlus](https://github.com/teikengstudio/XMPlus)：本项目所基于的上游项目，AGPL-3.0-or-later。
- [sld272/Ximalaya-Downloader-Next](https://github.com/sld272/Ximalaya-Downloader-Next)：登录签名与短信验证码流程的参考实现，AGPL-3.0-or-later。
