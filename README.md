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

XMPro 是基于**喜马拉雅极速版**内容接口的第三方 Android 收听客户端，使用 Kotlin 与 Jetpack Compose 从零实现，界面与交互贴合 Material 3 规范。

## 功能

### 收听

- **长篇选集区间** —— 上千集不用一集集翻，按每 100 集分段直接跳；播放队列也只保留这一段，前后切换不会跑出去。
- **待播队列** —— 在播放页随时查看和切换队列，同样支持分段，长专辑可以只挑其中一段来播。
- **后台播放** —— Media3 承载播放，通知栏、锁屏、蓝牙耳机皆可控制，切后台不断播。
- **跳过头尾** —— 两种模式：智能模式识别开头空白与结尾静音自动跳过；手动模式按全集比例设置裁剪点，几分钟的段子和几十分钟的长篇都适用。
- **倍速与定时** —— 播放倍速循环切换，定时关闭到点即停。
- **音质选择** —— 自动优先高音质，也可手动锁定档位。

### 书单

- **听更新 / 订阅 / 历史 / 收藏** 各自独立，互不干扰。
- **书签** —— 在任意位置打点，回头直接接着听。
- **主播主页** —— 从关注或粉丝列表进入，查看对方全部专辑。
- **播放进度云同步** —— 本地与云端进度取较新者，换设备接着听。

### 听时

- **本机听时** —— 显示当前设备可用听时，按设备计算，换账号不改变。
- **福利任务** —— 查看各档时长与最近到账时间，也可展开看接口返回的完整字段。
- **自动领取** —— 余额偏低时自动补充；领取失败会显示官方给出的原因。

### 账号与外观

- **多账号** —— 账号卡或账号列表点一下即切换，无需重新登录。
- **凭据迁移** —— 复制已登录账号的 Cookie，粘贴到另一台设备即可登录。
- **频道自定义** —— 首页频道可排序、隐藏、恢复默认。
- **外观** —— Android 12+ 动态取色，另有 7 套预设主题；深色模式与 OLED 纯黑可选。
- **备份恢复** —— 导出配置与书单，换机导入即恢复，可选择是否包含登录信息。

## 下载

从 [Releases](https://github.com/angusdevgo/XMPro/releases) 获取：

```
XMPro-<版本>-android-arm64-v8a.apk
SHA256SUMS
```

仅构建 `arm64-v8a`，安装前请确认设备架构；要求 Android 7.0（API 24）及以上。

应用内检查更新会直接下载并用 `SHA256SUMS` 校验后交给系统安装，不跳浏览器。

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

## 许可

**AGPL-3.0-or-later**

- [teikengstudio/XMPlus](https://github.com/teikengstudio/XMPlus) —— 本项目的功能与实现参考来源。
- [sld272/Ximalaya-Downloader-Next](https://github.com/sld272/Ximalaya-Downloader-Next) —— 登录与签名算法来源。
- 感谢 [LINUX DO](https://linux.do/) 社区的技术交流氛围。

本项目与喜马拉雅官方无关，仅供个人学习与自用，请使用自己的账号并遵守服务条款。
