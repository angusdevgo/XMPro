<div align="center">
  <img width="150" height="150" src="assets/branding/xmpro.png" alt="XMPro">
  <h1>XMPro</h1>
  <p>一个安静好用的喜马拉雅收听客户端 · Kotlin + Jetpack Compose</p>

  [![Android CI](https://github.com/angusdevgo/XMPro/actions/workflows/android.yml/badge.svg)](https://github.com/angusdevgo/XMPro/actions/workflows/android.yml)
  [![GitHub release](https://img.shields.io/github/release/angusdevgo/XMPro)](https://github.com/angusdevgo/XMPro/releases/latest)
  [![GitHub downloads](https://img.shields.io/github/downloads/angusdevgo/XMPro/total)](https://github.com/angusdevgo/XMPro/releases)
  [![License](https://img.shields.io/badge/license-AGPL--3.0--or--later-blue)](LICENSE)
</div>

<br/>

## 特点

**为长时间听书而做。** 上千集的长篇、通勤与睡前，是这个客户端最常出现的场景，界面和交互都围绕它取舍。

- **选集区间** —— 长篇不用一集集滑，直接指定「2401 到 2500」，区间内自动补拉分页，播放也只在这段里前后切换。
- **后台不掉线** —— Media3 承载播放，通知栏、锁屏、蓝牙耳机都能控制，切后台继续播。
- **听时看得见** —— 首页一句话告诉你剩余听时，支持一键查询与领取，播放进度定时同步到云端历史。
- **接管你的书单** —— 订阅、收藏、云端历史、常听更新各自独立；关注与粉丝列表可以点进主播主页看他的专辑。
- **频道自己排** —— 首页频道可排序、可隐藏、可恢复默认，编辑入口就在频道行末尾。
- **外观随你** —— Android 12+ 动态取色，另有 7 套预设主题；深色模式与 OLED 纯黑可选。
- **播放可细调** —— 音质档位、跳过片头、跳过片尾（到点自动切下一集）、倍速、定时关闭、待播队列。
- **配置能带走** —— 一键导出备份，换机导入即恢复；可选择性包含登录信息。
- **小巧** —— 单 ABI 安装包约 2.5 MB，界面全部原生 Compose 绘制。

## 下载

从 [Releases](https://github.com/angusdevgo/XMPro/releases) 获取最新安装包：

```
XMPro-<版本>-android-arm64-v8a.apk
SHA256SUMS
```

仅构建 `arm64-v8a`，安装前请确认设备架构；Android 版本要求 7.0（API 24）以上。

## 编译

工具链：JDK 17、Gradle 8.9、AGP 8.7.3、Kotlin 2.0.20；`compileSdk` 35、`minSdk` 24。

```sh
./android/gradlew -p android :app:assembleDebug
./android/gradlew -p android :app:testDebugUnitTest
```

正式构建需要 JKS 签名环境变量，签名材料不入库：

```sh
export ANDROID_KEYSTORE_PATH=/path/to/release.jks
export ANDROID_KEYSTORE_PASSWORD=...
export ANDROID_KEY_ALIAS=...
export ANDROID_KEY_PASSWORD=...
./android/gradlew -p android :app:assembleRelease
```

推送到 `v*` 标签时，CI 会用仓库 secrets 签名、校验签名与版本号，并把产物发布到 Releases。
`android/local.properties` 里的 `sdk.dir` 指向本机 Android SDK。

## 许可

以 **AGPL-3.0-or-later** 发布，见 [LICENSE](LICENSE)；它等于 GPL-3.0 的全部条款加第 13 条「网络提供」。
本项目是 [XMPlus](https://github.com/teikengstudio/XMPlus) 的修改版，登录相关实现参考了
[sld272/Ximalaya-Downloader-Next](https://github.com/sld272/Ximalaya-Downloader-Next)（两者同为 AGPL-3.0-or-later）。
分发或改造时请保留同样的许可并提供对应源码。

本项目与喜马拉雅官方无关，仅供个人学习与自用，请使用自己的账号并遵守服务条款。
