<div align="center">
  <img width="140" height="140" src="assets/branding/xmpro.png" alt="XMPro">
  <h1>XMPro</h1>
  <p>喜马拉雅第三方 Android 收听客户端 · Kotlin + Jetpack Compose</p>

  [![Android CI](https://github.com/angusdevgo/XMPro/actions/workflows/android.yml/badge.svg)](https://github.com/angusdevgo/XMPro/actions/workflows/android.yml)
  [![GitHub release](https://img.shields.io/github/release/angusdevgo/XMPro)](https://github.com/angusdevgo/XMPro/releases/latest)
  [![GitHub downloads](https://img.shields.io/github/downloads/angusdevgo/XMPro/total)](https://github.com/angusdevgo/XMPro/releases)
  [![License](https://img.shields.io/badge/license-AGPL--3.0--or--later-blue)](LICENSE)
</div>

<br/>

## 项目说明

XMPro 是基于喜马拉雅极速版内容接口的第三方 Android 客户端，使用 Kotlin 与 Jetpack Compose 实现，界面基于 Jetpack Compose Material 3 组件构建，采用自定义的悬浮导航与圆角视觉。

项目仅面向 Android 平台，仅构建 `arm64-v8a` 单一 ABI。

## 功能

**播放**

| 能力 | 说明 |
| :--- | :--- |
| 选集区间 | 长篇专辑按每 100 集分段，定位到任意区段；播放队列限定在该段内，切集不会越界 |
| 待播队列 | 播放页可展开队列并切换曲目，队列同样支持分段筛选 |
| 后台播放 | 由 Media3 `MediaSessionService` 承载，支持通知栏、锁屏与蓝牙设备控制 |
| 跳过头尾 | 智能模式基于音频静音检测跳过首尾空白；手动模式按全集比例设置裁剪点，适配不同时长节目 |
| 倍速与定时 | 播放倍速循环切换，定时关闭到点暂停 |
| 音质 | 默认自动选择最高可用档位，也可手动锁定 |

**内容**

| 能力 | 说明 |
| :--- | :--- |
| 书单分类 | 听更新、订阅、历史、收藏四类列表独立维护 |
| 书签 | 在任意播放位置记录，可从记录处继续播放 |
| 主播主页 | 由关注或粉丝列表进入，展示该主播的专辑列表 |
| 进度同步 | 播放进度定期写入云端历史，本地与云端取较新者作为续播位置 |

**听时**

| 能力 | 说明 |
| :--- | :--- |
| 余额查询 | 显示当前设备可用听时；该额度按设备计算，不随账号变化 |
| 福利任务 | 展示各档位时长与最近到账时间，可查看接口返回的完整字段 |
| 自动领取 | 余额低于阈值时自动补充；失败时展示服务端返回的原因 |

**账号与外观**

| 能力 | 说明 |
| :--- | :--- |
| 多账号 | 账号卡或列表点击即切换，无需重新登录；非当前账号仅提供切换入口 |
| 凭据迁移 | 导出已登录账号的 Cookie，可在其他设备导入登录 |
| 频道管理 | 首页频道支持排序、隐藏与恢复默认 |
| 外观 | Android 12+ 动态取色，内置 7 套预设主题；支持深色与 OLED 纯黑 |
| 备份恢复 | 导出配置与书单，导入即恢复，可选择是否包含登录信息 |

## 相对 XMPlus 的取舍

XMPlus 为 Flutter 跨平台实现，覆盖 Windows 与 Android。本项目只面向 Android，实现语言与部分功能范围随之调整。

### 新增

- 增加选集区间网格，每 100 集一段，点一下只看这一段。
- 增加待播列表区间，长专辑可只播其中一段。
- 增加应用内更新，下载后校验 SHA-256 再交给系统安装。
- 增加切换账号，账号卡与账号列表都能直接换号，不需要重新登录。
- 增加复制已登录账号的 Cookie，粘贴到其他设备即可登录。
- 增加福利任务面板，可看档位时长、最近到账时间与听时接口返回的全部字段。
- 增加关于页，含检查更新、反代加速、GitHub 仓库与作者署名。
- 增加耳机猫线描图标，支持自适应图标与 Android 13 主题图标。

### 优化

- 优化跳过头尾，增加基于静音检测的智能模式，手动模式改为按全集比例裁剪。
- 优化切集流程，为每次播放请求编号，丢弃过期响应。
- 优化历史与搜索结果播放，改为加载所属专辑队列，前后集可切换。
- 优化时间校验，时间戳统一按服务端时间校正，避免设备时间漂移导致请求被拒。
- 优化失败提示，领取与请求失败时直接展示服务端返回的原因。

### 修复

- 修复深色模式需要重进应用才生效。
- 修复片头跳过不生效，起播位置改为随媒体项一起下发。
- 修复已登录状态下无法新增手机号账号，弹窗闪现即消失。
- 修复听时余额较高时切换专辑被拒（ret=4），上报余额改为按一天上限截断。
- 修复听时与账号操作按钮被挤成两行、标签被拆开。

### 去除

- 移除下载与离线收听。
- 移除 FFmpeg / FFprobe 原生媒体工具链与转码。
- 移除 Windows 桌面端。
- 移除多 ABI 构建，仅保留 `arm64-v8a`。

## 技术栈

| 项目 | 版本 / 说明 |
| :--- | :--- |
| 语言 | Kotlin 2.0.20 |
| UI | Jetpack Compose，Material 3 |
| 播放 | AndroidX Media3（ExoPlayer + MediaSessionService） |
| 构建 | Gradle 8.9、AGP 8.7.3 |
| SDK | `compileSdk` 35、`minSdk` 24（Android 7.0） |
| ABI | `arm64-v8a` |

源码组织：

```
api/       接口封装、签名与协议解析
data/      持久化、多账号与听时账本
model/     数据模型
player/    播放控制与音频处理
ui/        Compose 界面与视图模型
updater/   版本检查、下载与校验
```

## 安装

从 [Releases](https://github.com/angusdevgo/XMPro/releases) 下载：

```
XMPro-<版本>-android-arm64-v8a.apk
SHA256SUMS
```

设备需为 `arm64-v8a` 架构、Android 7.0 及以上。应用内检查更新会下载安装包并与 `SHA256SUMS` 比对，校验通过后交由系统安装器处理。

## 构建

```sh
./android/gradlew -p android :app:assembleDebug
./android/gradlew -p android :app:testDebugUnitTest
```

发布构建需提供签名参数，签名材料不入库：

```sh
export ANDROID_KEYSTORE_PATH=/path/to/release.jks
export ANDROID_KEYSTORE_PASSWORD=...
export ANDROID_KEY_ALIAS=...
export ANDROID_KEY_PASSWORD=...
./android/gradlew -p android :app:assembleRelease
```

推送 `v*` 标签会触发 CI：使用仓库 secrets 签名，校验签名、包名与版本号，随后将安装包与校验文件上传至 Releases。

## 许可

[AGPL-3.0-or-later](LICENSE)

- [teikengstudio/XMPlus](https://github.com/teikengstudio/XMPlus)：功能与实现参考。
- [sld272/Ximalaya-Downloader-Next](https://github.com/sld272/Ximalaya-Downloader-Next)：登录与签名算法参考。
- 感谢 [LINUX DO](https://linux.do/) 社区提供的技术交流环境。

本项目与喜马拉雅官方无关，仅供个人学习与自用，请使用自有账号并遵守服务条款。
