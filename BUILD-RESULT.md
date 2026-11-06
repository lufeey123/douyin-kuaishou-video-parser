# 拾影 1.4.2 构建与验证

- 环境：本地 Alpine Linux aarch64、OpenJDK 17，原生 aapt2 / zipalign、Google D8 与 apksig。
- 包名 `com.example.videoparser`，versionCode 8，versionName 1.4.2，最低 Android 10，targetSdk 35。
- 调试产物：`app/build/outputs/apk/debug/app-debug.apk`。

## 配色与布局

根据用户要求增加适量玻璃感，同时收紧布局。冷白青墨配色，柔和蓝绿渐变背景，输入和结果卡使用半透明白色材质与细高光。按钮、文字保持实色，辅助文字调为 #526570 保持可读性。没有实例化 GlassSurface，也未启动实时采样或 RuntimeShader；因此此版本的玻璃感是静态材质，不声称动态折射与模糊。

品牌20sp、logo22dp，输入固定88dp内部滚动，提示和右136dp解析按钮并排；屏宽小于360dp或fontScale不小于1.3时自动纵排。单条结果隐藏统计/编号和重复完成状态，文字详情按钮与实色下载按钮拉开主次。详情叠加近实色衬底，避免下层文字透出重影。保留双平台、批量、取消、重试、下载及生命周期逻辑。

## 检查

- 全部 Java 编译及95项业务回归通过：媒体策略21、抖音24、双平台/快手29、批量21。
- 资源 XML 与脚本语法通过；aapt2、Java8字节码至DEX及APK打包通过。
- 独立只读审查通过；已处理窄屏按钮挤压、半透明详情透字两项风险。
- 原有MIT源码声明和APK assets保留，应用内可查看开源说明。

未完成Android真机或模拟器截图、安装、键盘和字体缩放测试。当前环境没有可连接设备，源码审查不能替代视觉验证。使用本地工具链构建，未声称完整Gradle构建通过。

最终APK：v3签名有效，与1.4.1证书一致；ZIP对齐、CRC、DEX SHA-1 / Adler32通过。

SHA-256：`9b30cab8cbe2144ae181c45a953c91fe624547b07cbfd8d77f6f57cceedc969b`
