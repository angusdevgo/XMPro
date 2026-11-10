<div align="center">
  <img width="160" height="160" src="assets/branding/xmplus.png" alt="XMPro">
  <h1>XMPro</h1>
  <p>使用 Flutter 开发的喜马拉雅第三方下载与收听客户端</p>
</div>

<br/>

## 功能

- 多账号登录、专辑搜索、播放队列、在线收听和听时记录。
- 下载任务、本地媒体管理、可选转码与音频标签；自动下载需单独启用。
- 仅提供 Android arm64-v8a 安装包，内含源码构建的 FFmpeg / FFprobe 和 LAME，支持原始流保存、MP3、M4A、M4B、FLAC、标签及封面处理。
- 备份设置、书架、历史和可选账号信息；含登录凭据的备份未加密，请妥善保管。
- 播放与下载设置、持久音频地址缓存、主题和 GitHub 稳定版更新检查。



## 编译

工具链：Kotlin + Jetpack Compose、JDK 17、Gradle 8.9、AGP 8.7.3；`compileSdk` 35、`minSdk` 24，仅 `arm64-v8a`，NDK 为 28.2.13676358。

先在 Linux x86_64 安装 Android NDK 28.2.13676358、`jq`、`make`、`tar`、`sha256sum`，构建随包 FFmpeg 后再编译：

```sh
bash tool/build_android_media.sh arm64-v8a
./android/gradlew -p android :app:assembleDebug
```

正式构建必须配置 JKS 签名环境变量：`ANDROID_KEYSTORE_PATH`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_PASSWORD`、`ANDROID_KEY_ALIAS`。签名材料不得提交到仓库。

> 正在从 Flutter 迁移到纯 Kotlin：`lib/`、`pubspec.yaml`、`test/` 暂时作为逐行对照参考保留，不再参与构建，迁移完成后删除。迁移前的可构建版本见 git 标签 `flutter-final`。
