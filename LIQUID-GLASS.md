# 玻璃材质与开源来源

1.4.2 使用静态半透明材质和高光描边，不启用实时背景采样、折射或模糊。实时渲染适配代码仍保留在源码中。

## 来源

- 项目：[QmDeve/AndroidLiquidGlassView](https://github.com/QmDeve/AndroidLiquidGlassView)
- 提交：`d4238c557e46271f29ec0e3d3ee211bcf58dc2e0`
- 许可证：[MIT](licenses/QmDeve-MIT.txt)

Java 渲染文件保留 Donny Yang 的版权声明，AGSL 保留 QmDeve 及贡献者声明。完整许可也打包在 APK assets 中，可从应用内“帮助 → 开源说明”查看。

## 保留的渲染模块

适配层基于上游 RenderNode / RuntimeShader 核心，保留原始 AGSL，未引入上游整套控件、触摸跟踪或 AndroidX 弹簧动画。

以下是保留模块的实现路径，当前页面未启用：

| 系统 | 渲染方式 |
| --- | --- |
| Android 13+ | RuntimeShader 折射、色散与背景模糊 |
| Android 12 | RenderEffect 背景模糊 |
| Android 10–11 | 静态半透明材质 |

采样上限为单边 4096 像素、总计 400 万像素，异常时回退至静态材质。采样层与前景控件分离，并使用屏幕坐标对齐、重入保护和窗口移除清理。
