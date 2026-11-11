<div align="center">
  <img width="140" height="140" src="assets/branding/xmpro.png" alt="XMPro">
  <h1>XMPro</h1>
  <p>一个安静好用的喜马拉雅收听客户端 · Kotlin + Jetpack Compose</p>

  [![Android CI](https://github.com/angusdevgo/XMPro/actions/workflows/android.yml/badge.svg)](https://github.com/angusdevgo/XMPro/actions/workflows/android.yml)
  [![GitHub release](https://img.shields.io/github/release/angusdevgo/XMPro)](https://github.com/angusdevgo/XMPro/releases/latest)
  [![GitHub downloads](https://img.shields.io/github/downloads/angusdevgo/XMPro/total)](https://github.com/angusdevgo/XMPro/releases)
  [![License](https://img.shields.io/badge/license-AGPL--3.0--or--later-blue)](LICENSE)
</div>

<br/>

## 简介

XMPro 是基于**喜马拉雅**内容的第三方 Android 收听客户端，界面与交互参考了
[XMPlus](https://github.com/teikengstudio/XMPlus) 并做了重构，使用 Kotlin + Jetpack Compose
原生实现。整个客户端围绕「长时间听书」这个场景设计：上千集的长篇、通勤与睡前。

## 功能

- **选集区间** —— 长篇不用一集集滑，直接指定「2401 到 2500」，自动补拉所需分页，播放也只在这段内前后切换。
- **后台播放** —— Media3 承载播放，通知栏、锁屏、蓝牙耳机都能控制，切后台继续播。
- **听时管理** —— 显示剩余听时，支持查询与领取；播放进度定时同步到云端历史。
- **书单管理** —— 听更新、订阅、历史、收藏、书签各自独立；关注与粉丝列表可进入主播主页查看其专辑。
- **频道自定义** —— 首页频道可排序、隐藏、恢复默认。
- **外观** —— Android 12+ 动态取色，另有 7 套预设主题；深色模式与 OLED 纯黑可选。
- **播放设置** —— 音质档位、跳过片头、跳过片尾（到点自动切下一集）、倍速、定时关闭、待播队列。
- **备份恢复** —— 一键导出配置与书单，换机导入即恢复，可选择是否包含登录信息。

## 下载

从 [Releases](https://github.com/angusdevgo/XMPro/releases) 获取：

```
XMPro-<版本>-android-arm64-v8a.apk
SHA256SUMS
```

仅构建 `arm64-v8a`，安装前请确认设备架构；要求 Android 7.0（API 24）及以上。

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

## 🤝 Acknowledgements & License

开源协议：**AGPL-3.0-or-later** 
特别致谢开源项目： [XMPlus](https://github.com/teikengstudio/XMPlus) 
特别致谢开源项目：[sld272/Ximalaya-Downloader-Next](https://github.com/sld272/Ximalaya-Downloader-Next)
本项目与喜马拉雅官方无关，仅供个人学习与自用，请使用自己的账号并遵守服务条款。
