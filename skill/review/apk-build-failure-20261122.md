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


## 后续构建复测与第二批修复

### 旧 run 重跑问题
- 用户再次看到失败时，run 45028275802 的 attempt 2 仍使用旧 SHA `9134ba95c5aaa93eda13bb837b91e667f7bd9b60`，因此重复报重复资源 key。修复提交 `9428ccc...` 并未包含在该 run 中。
- 为避免再次重跑旧 SHA，在功能分支工作流增加对 `feature/tablet-live-immersive` 的 push 触发，同时保留手动触发。

### 新 SHA 暴露的资源问题
- 新提交构建发现 `values-en/strings.xml` 的 `cloud_drive_cookie_placeholder` 文案含未转义撇号，AAPT2 报告 Invalid unicode escape sequence / invalid string resource。
- 将英文文案调整为不含撇号的 “Paste the provider cookie”。

### 新 SHA 暴露的 Kotlin 编译问题
- `ConfigRepository.kt` 缺少 object 最终闭合括号。
- `SearchSourcesState.kt` 与 `SearchViewModel.kt` 对可空 `SourceBean.key` 直接作为非空字符串使用。
- `SpiderLoader.kt` 调用 `replaceCache` 时漏传 `epoch`。
- `FileUtils.kt` 把 Gson 的 Boolean 属性 `isJsonPrimitive` 再当作 JsonPrimitive 访问 `isString`。
- `VideoGestureHandler.kt` 重复声明并重复赋值 `downX`。
- `PlayContainer.kt` 保留了已删除专用云盘播放流程的投屏限制，并引用已删除的资源字符串。
- `JarLoader.kt` 对跨模块 Pair 属性重复访问导致 Kotlin 无法 smart cast。
- 已按对应类型契约修复以上问题，代码变更和说明已写入功能分支。

### 当前验证状态
- 之前的新 SHA 构建已经通过 Android 资源合并并进入 Kotlin 编译阶段，暴露上述编译错误。
- 第二批修复已推送，push 触发的新构建正在执行/等待确认。
- APK 尚未成功生成；签名验证与 artifact 上传仍未执行。


### ConfigRepository 末尾括号二次校正
- 最新构建 `45029397617` 已通过资源合并，Kotlin 编译报告 `ConfigRepository.kt:88` 多余的顶层右括号。
- 对照实际提交 diff 与源码重新核算后，将文件恢复为 9 对 9 的花括号；保留函数 lambda 与 object 各自唯一闭合。
- 修复提交：`0144f797eed92b77d739869fe69aeff88e716880`。等待下一次新 SHA 构建验证。
