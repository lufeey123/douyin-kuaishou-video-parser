# 拾影 · 抖音 / 快手视频解析工具

原生 Android 分享链接解析与下载工具，支持抖音、快手及最多 20 条混合批量解析。无广告、无统计、无需登录，解析在应用内完成，不依赖远程解析服务。

**Android 10+ · Java · 当前版本 1.4.2 · [GPL-3.0](LICENSE)**

[查看安装包发布](https://github.com/lufeey123/douyin-kuaishou-video-parser/releases) · [反馈问题](https://github.com/lufeey123/douyin-kuaishou-video-parser/issues)

> APK 在 Releases 中发布。源码不包含签名私钥，当前安装包为调试版。平台网页结构或访问限制可能导致解析失败；播放源不保证所有作品都无水印。

## 功能

- 粘贴抖音或快手分享文本，自动识别平台；支持单条及混合批量，最多 20 条不同链接。
- 批量去重、进度与逐项错误、停止保留已完成项、重试失败/未完成项；复制全部成功链接或主动下载成功项。
- 接收其他应用的“分享”文本，也可通过系统打开支持的抖音、快手 HTTPS 链接。
- 展示标题与封面，复制视频地址，调用外部播放器。
- 通过 Android 系统下载管理器保存至公共 `Download` 目录，无需存储权限。
- 限制输入长度、网页大小、网络超时与重定向；校验页面和媒体域名。
- 不包含广告、统计、登录、远程解析服务、WebView 或常驻服务。

## 界面更新（1.4.2）

冷白与青墨配色，蓝绿色轻渐变背景，输入和结果容器采用静态半透明材质、细高光描边。按钮和文字保持实色，以保证可读性。适量增加通透感，保留 MD3 风格的操作层级，不启用曾在设备截图中出现矩形边缘和重影的实时背景采样。

顶部与输入布局进一步收紧，输入内部滚动；底部提示与主操作并排。单条结果隐藏冗余统计和序号，批量结果保留统计及工具。详情与下载有明确主次。界面使用原生 Android View 实现，没有引入 Google Material Components 库。

保留 [AndroidLiquidGlassView](https://github.com/QmDeve/AndroidLiquidGlassView) 的适配源码、出处及 MIT 许可，见 [液态玻璃说明](LIQUID-GLASS.md)。详情见 [界面改动](UI-CHANGES.md)。当前环境无法进行 Android 真机截图与交互测试。

## 编译 APK

推荐在电脑 Android Studio 中打开本目录，使用 JDK 17，安装 Android SDK Platform 35 和 Build Tools 35。Gradle Wrapper 使用 Gradle 8.9，Android Gradle Plugin 8.7.3。

首次构建需要联网下载官方构建依赖。命令行配置好 Android SDK 后执行：

```sh
./gradlew :app:assembleDebug
```

目录不允许执行脚本时，可使用 `sh gradlew :app:assembleDebug`。此启动器默认将 Gradle 缓存放在项目内的 `.gradle-user-home/`。

Windows 使用 `gradlew.bat :app:assembleDebug`。APK 位于：

```
app/build/outputs/apk/debug/app-debug.apk
```

如需指定 SDK，在本目录创建不提交的 `local.properties`：

```properties
sdk.dir=/你的/Android/Sdk
```

发布版本需自行配置签名；源码包不包含私钥。当前手机 Alpine ARM64 环境已通过原生 `aapt2`、Java D8 和 Google apksig 完成调试 APK 打包，产物位于上述路径。

### 当前手机上的本地构建

本环境已安装 Alpine 原生 `aapt2` 和 `zipalign`。在本项目目录执行：

```sh
sh tools/build-local.sh .verify/android.jar .build-research/r8.jar .build-research/apksig-mirror.jar
```

该脚本使用原生资源工具、Java 8 字节码编译、D8、zipalign 和 APK v3 调试签名，不依赖完整桌面 SDK 或 Gradle。生成的调试密钥保留在本机 `.build-native/debug.p12`，不要用于正式发布。源码 ZIP 不包含构建缓存、依赖 JAR 或密钥。

依赖来源：Android 35 平台 JAR；Alpine edge/testing 的 `android-build-tools`；Google 官方 R8 `https://storage.googleapis.com/r8-releases/raw/8.7.18/r8.jar`；Google Maven 的 `com.android.tools.build:apksig:8.7.3`（本次通过阿里云 Google Maven 镜像下载）。详细结果见 `BUILD-RESULT.md`。

## 使用

1. 从抖音或快手复制一条或多条分享文本，粘贴后点击解析。多条建议每行一条，两个平台可混合。最多 20 条不同链接、64000 字符；相同链接去重，超出条数会提示分批。
2. 列表逐条显示进度、成功结果或失败原因。点击停止会保留已完成结果；可重试失败和未完成项。单项失败不会阻断后续项。
3. 成功项可查看详情、封面、播放、复制和下载。也可复制全部成功链接，或点击“下载成功项”提交下载；解析本身不会自动下载。本次界面会话内防止重复提交同一链接/播放地址。
4. 下载按钮表示提交给系统下载管理器，实际进度及失败原因以系统下载界面为准。

解析需联网，会向分享链接所属平台（抖音或快手）及其媒体域名发送请求；应用不上传其他本地文件。仅下载你有权保存的内容。

## 双平台与批量

`VideoParser` 根据域名自动分流，保留现有抖音解析。快手支持 `v.kuaishou.com` 短链及公开作品页，包含 `v.m.chenzhongtech.com` 分享跳转；从 `window.INIT_STATE` 提取 `mainMvUrls`、封面和标题，通过 `share_info` 关联外链 ID，避免与内部数字 ID 混淆或选中推荐作品。

快手真实样例已完成 Java 解析器联网验证，视频请求返回 HTTP 206、`video/mp4`，读取的首 4096 字节包含 MP4 `ftyp` 文件头。1.2.0 已进一步完整下载样例并抽帧检查，详见 [水印研究](WATERMARK-RESEARCH.md)；新版界面尚未真机操作。

封面、下载和外部播放统一使用对应平台 Referer 与手机 UA；外部应用是否接受播放请求头取决于接收应用。快手使用页面原始播放源，并增加真实 manifest 备用版本，不猜测改写签名地址。样例的 11 个抽样画面未见快手平台水印，但原片字幕和画面内嵌内容仍保留；应用不声称自动识别所有水印。保留抖音支持不代表解决所有抖音页面兼容性问题。

批量任务串行执行，取消后丢弃当前请求的迟到结果并停止后续项；已完成结果可继续使用。没有实现界面销毁或进程退出后的持久化批次恢复。

## 解析边界

解析器读取公开网页中包含的视频结构化数据，不绕过登录、验证码、付费或地区限制。页面结构变更、反爬校验、私密/已删除作品、图集、直播等情况可能失败，应用会提示错误。

将播放端点路径中的 `playwm` 改为 `play` 是兼容旧分享页的处理，不能保证平台仍提供无水印视频。不提供“永久有效”承诺，解析地址可能有时效，失效后需重新解析。结果中的标题来自作品数据，缺失时显示默认标题。

封面和下载链接受固定 HTTPS 域名白名单限制，新 CDN 域名需要更新 `NetworkPolicy.java`。系统下载管理器负责后续传输与重定向，应用不会将“下载已提交”当作“视频已验证并保存”。

## 文件结构

```text
app/src/main/
  AndroidManifest.xml
  java/com/example/videoparser/
    MainActivity.java       原生界面、分享入口与异步操作
    DownloadHelper.java     系统下载管理器
    NetworkPolicy.java      媒体 URL 白名单
    parser/                 VideoParser 提取分流，BatchParser 串行队列，两个平台独立解析
  res/                      主题与矢量图标
tests/                      离线验证
tools/                      检查脚本
```

应用为独立 Android 工程，运行时不依赖原始 ApiGo 流程包。

## 已执行的检查

- 对 Android 35 的 `android.jar` 编译全部 Java 源码：通过；系统栏兼容代码有弃用警告，保留以支持 Android 10。
- 媒体地址白名单：21 个离线断言通过，包括快手 CDN 与平台隔离。
- 抖音页面解析：24 个离线断言通过。
- 双平台路由与快手解析：29 个离线断言通过，新增播放清单、编码兼容选择、隐藏/音频版本排除和备用 CDN 验证。
- 批量提取与队列：21 个离线断言通过，覆盖去重、混合平台、上限、签名保留、逐项失败继续和取消/中断行为。总计 95 项。
- Manifest、主题、图标 XML 可解析；Shell 脚本语法通过。
- 已完成原生资源打包、Java → DEX、ZIP 对齐和 APK 签名；签名校验通过，已生成可安装调试 APK。
- 未完成安装和真机界面测试。已联网请求原流程中的旧分享链接：分享页 HTTP 200，但移动端响应没有播放字段，尚未实现该链接端到端解析成功。Gradle 构建因 Google 依赖请求长时间未响应而停止，APK 由上述等效本地工具链生成。

离线检查脚本用法（依赖文件由调用者提供）：

```sh
sh tools/check.sh /path/to/android-35/android.jar /path/to/json-20240303.jar
```

完整 UI 编译检查会调用 PATH 中的 `aapt2` 编译资源并生成 `R.java`；没有该工具时请使用 Android Studio/Gradle 构建。

第二个 JAR 为 Maven 坐标 `org.json:json:20240303`，只用于本地运行测试，应用使用 Android 内置 `org.json`。不传参数时仅验证不依赖 Android 的媒体地址白名单。

Gradle Wrapper JAR 来自 Gradle 官方 v8.9.0 仓库，采用 Apache License 2.0：
https://github.com/gradle/gradle/tree/v8.9.0/gradle/wrapper

## 反馈问题

提交 Issue 时请附上应用版本、Android 版本、操作步骤和错误提示；如方便，可提供可公开访问的样例分享链接。请不要上传账户 Cookie、令牌或签名私钥。

## 许可与第三方代码

第三方液态玻璃适配模块来自 [QmDeve/AndroidLiquidGlassView](https://github.com/QmDeve/AndroidLiquidGlassView)，遵循 MIT 许可，完整声明位于 [licenses/QmDeve-MIT.txt](licenses/QmDeve-MIT.txt)，修改记录见 [LIQUID-GLASS.md](LIQUID-GLASS.md)。当前界面采用静态半透明材质。

Copyright (C) 2026 lufeey123。

除另有声明的第三方代码外，本项目采用 **GNU GPL v3.0（GPL-3.0-only）** 许可证，完整条款见 [LICENSE](LICENSE)。

欢迎 Fork、修改和二次开发，也允许商用。分发原版或修改版时，请保留版权及许可声明；分发修改版须标明修改情况和相关日期，并遵守 GPL-3.0 提供对应源码等要求。仅在本地使用的修改无需公开，也不要求将修改提交回本仓库。

欢迎注明原项目地址：https://github.com/lufeey123/douyin-kuaishou-video-parser 。这是一项署名建议，不是额外的许可证限制。本项目按许可证约定不提供担保。

第三方代码保留各自的许可证和版权声明，包括上述 MIT 模块及 Apache-2.0 许可的 Gradle Wrapper。项目整体的 GPL-3.0 声明不替换这些第三方许可。
