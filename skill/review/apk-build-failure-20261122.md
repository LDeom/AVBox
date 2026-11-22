# APK 构建失败原因与修复记录

## 运行记录
- GitHub Actions：Build Tablet Test APK
- Run ID：45028275802
- 分支：feature/tablet-live-immersive
- 构建提交：9134ba95c5aaa93eda13bb837b91e667f7bd9b60
- 结果：失败，失败步骤为 `:app:assembleDebug` 的资源合并阶段。

## 根因
Gradle 日志报告 Android 资源合并时出现重复资源项：
- `app/src/main/res/values/strings.xml`：`live_now_playing` 分别定义为“正在播放”和“正在播出”。
- `app/src/main/res/values-en/strings.xml`：`live_now_playing` 分别定义为“PLAYING”和“Now Playing”。

这会导致 AAPT2 资源合并失败，APK 编译未完成；APK 签名验证与 artifact 上传步骤因此被跳过。日志中的 Node.js 弃用警告不是本次失败原因。

## 修复
- 简体中文资源保留首个 `live_now_playing` 定义，删除后置重复定义。
- 英文资源保留首个 `live_now_playing` 定义，删除后置重复定义。
- 对繁体中文（台湾）与繁体中文（香港）资源文件进行重复 key 检查，未发现重复项。
- 同步记录到 `skill/history/features.md`。

## 验证状态
- 修复前：已由 GitHub Actions 日志确认资源合并失败原因。
- 修复后：已通过源码文本检查确认简体中文与英文各只剩一个 `live_now_playing` 定义；繁体资源无重复 key。
- 尚未重新运行 Gradle 构建、单元测试或 APK 打包，因此不能宣称构建已恢复成功。下一次构建用于验证该修复并检查后续编译阶段。


## 第二轮构建结果与第三轮修复
- Run 45028867509（旧提交 `85079e86d0f0e6855e9fd77de1957b9547c0b7b6`）日志显示：`values-en/strings.xml:656` 的 `cloud_drive_cookie_placeholder` 含无效 Unicode 转义序列，导致 AAPT2 资源编译失败。该错误已在后续提交中改为普通文本。
- Run 45029067532（提交 `cb270516b5bc24a7c72960f6b55d2821aa0a6d12`）已通过资源阶段，但 Kotlin 编译失败：`ConfigRepository.kt:86` 缺少闭合大括号。
- 检查确认文件末尾只闭合了 `switchLiveSource` 的 synchronized lambda，遗漏 `ConfigRepository` 对象闭合；已补齐，提交 `fd4f1e797c3c4f36f52f5d8ecd83f521b2d204c6`。
- 后续自动构建尚未完成；Run 45029122382 在检查时仍处于 Kotlin 编译阶段。APK 产物尚未验证，签名步骤尚未执行。
