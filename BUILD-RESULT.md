# 1.4.2 构建记录

已完成真机测试。

| 项目 | 版本 |
| --- | --- |
| 包名 | `com.example.videoparser` |
| 应用版本 | 1.4.2（versionCode 8） |
| 最低系统 | Android 10 |
| targetSdk | 35 |
| 构建环境 | Alpine Linux aarch64、OpenJDK 17 |
| 打包工具 | aapt2、D8、zipalign、apksig |

## 本地构建

```sh
sh tools/check.sh .verify/android.jar .verify/json.jar
sh tools/build-local.sh .verify/android.jar .build-research/r8.jar .build-research/apksig-mirror.jar
```

这些 JAR 需要自行准备，不包含在源码包内。Android Studio 构建方法见 [README](README.md)。APK 输出至 `app/build/outputs/apk/debug/app-debug.apk`，使用本地调试签名。

## 检查结果

- Java、资源及 DEX 编译通过。
- 95 项离线检查通过：媒体策略 21、抖音解析 24、双平台解析 29、批量任务 21。
- APK v3 签名有效，与 1.4.1 证书一致，可覆盖安装。
- ZIP 对齐、CRC、DEX SHA-1 和 Adler32 校验通过。

本版安装包由上述本地工具链生成。

SHA-256：

```text
9b30cab8cbe2144ae181c45a953c91fe624547b07cbfd8d77f6f57cceedc969b
```
