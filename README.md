# 拾影

一个轻量的安卓视频解析工具，支持抖音、快手。复制分享内容，粘贴进来就能解析，也可以一次处理多个链接。

无广告，不需要注册。支持 Android 10 及以上，已经过真机测试。

[下载安装包](https://github.com/lufeey123/douyin-kuaishou-video-parser/releases/latest) · [反馈问题](https://github.com/lufeey123/douyin-kuaishou-video-parser/issues)

## 能做什么

- 解析抖音、快手分享链接，两个平台可以混合粘贴。
- 每次最多 20 条，自动去重，失败的可以单独重试。
- 查看视频标题、封面，播放视频或复制播放地址。
- 单个下载、批量下载，保存到手机的 `Download` 文件夹。
- 从其他应用分享文字到拾影，不用来回复制粘贴。

## 怎么用

1. 在抖音或快手复制作品的分享内容。
2. 打开拾影，点击“粘贴”，再点“开始解析”。
3. 解析完成后，查看详情或下载视频。

批量解析时，每行放一条分享内容即可。任务可以中途停止，已完成的结果会保留。

下载由系统下载管理器处理，进度可以在系统通知中查看。视频链接可能过期，失效后重新解析；水印情况以实际视频为准。

## 当前版本

**v1.4.2**：调整了配色和布局，增加轻玻璃质感，收紧输入区，简化单条结果显示。窄屏和大字体下会调整解析按钮排列。

当前 APK 为调试版。更多改动见 [更新记录](UI-CHANGES.md)。

## 自己编译

使用 Android Studio 打开项目，准备 JDK 17、Android SDK 35 和 Build Tools 35，然后运行：

```sh
sh gradlew :app:assembleDebug
```

Windows 使用 `gradlew.bat :app:assembleDebug`。首次构建需要下载依赖，生成的 APK 在 `app/build/outputs/apk/debug/`。

发布版本请配置自己的签名，仓库不包含私钥。本地构建和检查命令见 [构建说明](BUILD-RESULT.md)。项目使用原生 Java 和 Android View，没有引入 Compose。

## 反馈

遇到解析失败或界面问题，可以提 [Issue](https://github.com/lufeey123/douyin-kuaishou-video-parser/issues)。带上应用版本、手机系统版本和操作步骤，有公开样例链接的话也可以一起附上。

只支持公开可访问的视频作品。私密、已删除、需要登录验证的作品，以及图集、直播等内容可能无法解析。请尊重作者版权，仅保存有权使用的内容。

## 开源与二创

Copyright (C) 2026 lufeey123。

本项目采用 [GPL-3.0-only](LICENSE)，欢迎 Fork、修改和商用。分发时请保留版权和许可声明；发布修改版需注明修改情况及日期，并按许可证要求提供对应源码。

欢迎注明原项目：[lufeey123/douyin-kuaishou-video-parser](https://github.com/lufeey123/douyin-kuaishou-video-parser)。

玻璃模块参考了 [AndroidLiquidGlassView](https://github.com/QmDeve/AndroidLiquidGlassView)，保留其 [MIT 许可](licenses/QmDeve-MIT.txt)，当前界面使用静态半透明材质，详见 [模块说明](LIQUID-GLASS.md)。Gradle Wrapper 使用 [Apache-2.0 许可](https://github.com/gradle/gradle/tree/v8.9.0/gradle/wrapper)。第三方代码保留原有版权和许可。
