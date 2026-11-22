# 第5阶段：播放器稳定性与旋转问题审查（第一轮）

## 审查范围
本轮按用户要求直接从第5阶段开始，暂不推进第4阶段。审查点包括直播播放器方向切换、Activity 配置变更、播放器/引擎生命周期、页面销毁与渲染容器所有权。重点对应用户此前反馈的“竖屏转横屏像重启软件”。

## 当前架构约束
- 播放器内核由进程级 `PlaybackEngine` 持有，页面只负责挂载/摘除视图；不能在 Activity 普通配置变化时随意释放共享内核。
- `PlaybackService` 托管引擎生命周期；直播页通过 `PlaybackService.engine()` 取得引擎，并调用 `enterLive()`。
- 改动播放器归属、跨页复用、后台行为或页面职责前，必须遵守 `skill/avbox-playback-service-spec.md` 的所有权与挂摘协议。

## 本轮静态核实

### 1. LivePlayActivity 方向变化保护
- `AndroidManifest.xml` 中 `LivePlayActivity` 声明了 `orientation|screenSize|keyboardHidden|screenLayout|smallestScreenSize|density|uiMode|colorMode` 的 `configChanges`。
- `LivePlayActivity.applyOrientationPolicy()` 明确覆写为空操作，避免 `BaseActivity.onResume()` 和 `BaseActivity.onConfigurationChanged()` 再次套用设备方向策略。
- `LivePlayActivity.onConfigurationChanged()` 记录 PID、方向、屏幕 dp、requestedOrientation，并同步 ViewModel 的 rotating/landscape 状态。
- `LivePlayActivity.onDestroy()` 只有在 `isFinishing && !isChangingConfigurations` 时才调用 `PlaybackService.peek()?.exitLive()`；配置变化销毁不会主动终止直播会话。
- `applyLiveOrientation()` 在全屏时使用 `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`，退出全屏时使用 `SCREEN_ORIENTATION_UNSPECIFIED`。

### 2. BaseActivity 方向策略冲突
- `BaseActivity.onCreate()`、`onResume()` 和 `onConfigurationChanged()` 都会调用 `applyOrientationPolicy()`。
- LivePlayActivity 已覆写该方法为空操作，因此当前代码路径没有证据表明 BaseActivity 的方向策略会覆盖直播页方向请求。
- 不应在没有复现证据前再叠加第二套方向锁定策略。

### 3. 播放器引擎与直播会话
- `PlaybackEngine.enterLive()` 会切换 liveMode、取消 idle release，并在进入直播时释放旧点播内核；这是进入直播模式的行为，不应在方向变化回调中重复调用。
- `PlaybackEngine.enterLiveState()` 在直播被点播页接管后用于恢复直播模式；LivePlayActivity.onResume() 只在确实发生接管时重播当前频道。
- `PlaybackEngine.exitLive()` 会释放直播内核、退出 liveMode 并安排空闲释放；当前 LivePlayActivity 的配置变化销毁分支不会调用它。
- 方向变化相关的渲染 Surface/Texture 解绑与重新挂接仍需结合渲染宿主实现和真机日志验证，当前仅确认了生命周期入口。

## 初步结论
- 静态代码已经具备避免 LivePlayActivity 因常规方向配置变化而重建的主要措施；目前没有足够证据支持再盲目修改 Manifest 或增加方向锁。
- 若用户仍观察到“像重启”，必须用日志区分三种情况：Activity 被重建（onCreate/onDestroy PID 相同）、进程重启（PID 改变）、仅播放器/Surface 重建（Activity 未重建但出现内核初始化日志）。LivePlayActivity 已记录 PID 与生命周期信息，可据此定位。
- 下一轮继续审查 Surface/Texture 宿主在配置变化时的解绑、重建和重复释放顺序，以及点播页 PlayContainer 的生命周期重入。优先寻找明确缺陷，再做最小修复。

## 验证状态
- 已完成：GitHub 分支源码静态读取、Manifest 与 Activity/BaseActivity/PlaybackEngine 生命周期交叉审查。
- 未完成：Gradle 编译、单元测试执行、Android 设备旋转复现、Surface/Texture 生命周期实测。
- 本轮未修改播放器核心逻辑、未构建 APK、未发布 Release。


## 第二轮：BasePlayerView 释放路径修复
### 发现
- `BasePlayerView.release()` 原先把渲染视图的移除与 `render.release()` 放在 `lastReportedPlayState != IDLE` 条件内。
- 播放器内核已创建渲染视图、但状态仍为 IDLE 的边界情况下，`kernelPlayer` 会被释放并置空，而 `activeRenderView` 可能仍留在 `playerFrame`，Surface/Texture 与视图引用没有被统一清理。

### 修复
- 将渲染视图从容器移除、调用渲染器 release、清空 activeRenderView，以及关闭 keepScreenOn 调整为无条件执行。
- 仅保留进度采集、保存、播放位置复位和状态派发在原有 hadActiveState 条件内，避免改变 IDLE 状态下的进度/状态语义。
- 提交：`0efd567fdc95cf659781b41f535f3ddae6e168ae`。

### 验证与风险
- 已静态检查修改前后的条件边界；未执行 Gradle 编译、单元测试或 Surface/Texture 真机测试。
- 后续需验证 IDLE/ PREPARING/ PLAYING 三种状态下 release 幂等性，以及 SurfaceView/TextureView 释放后重新创建和重绑定。


## 第三轮：Surface/Texture 释放顺序与页面旋转边界
### 交叉核实
- `DetailActivity` 在 Manifest 声明 `orientation|screenSize`，`LivePlayActivity` 声明更完整的显示配置变化项；正常设备旋转应由各自 Activity 的 `onConfigurationChanged` 接收，而不是默认销毁重建。
- `LivePlayActivity` 覆写 `applyOrientationPolicy()`，因此不会被 `BaseActivity` 的手机竖屏策略覆盖；全屏方向由 `applyLiveOrientation()` 统一设为 `SENSOR_LANDSCAPE`，退出全屏恢复 `UNSPECIFIED`。
- `LivePlayActivity` 已记录 PID、onPause/onStop/onDestroy、changingConfigurations/finishing 与配置方向。若旋转时 PID 不变且没有 onDestroy，属于配置变化处理；若出现 onDestroy/onCreate 但 PID 不变，属于 Activity 重建；PID 改变才是进程重启。
- `EngineSurfaceRenderView.release()` 和 `EngineTextureRenderView.release()` 都会先要求 KernelPlayer 解除视频输出，再释放 Surface/SurfaceTexture 资源。原 `BasePlayerView.release()` 却先释放 KernelPlayer/ExoPlayer，之后才调用 render.release()，导致渲染宿主的解除输出操作发生在内核销毁之后，调用顺序不一致。

### 修复
- 调整 `BasePlayerView.release()`：先从容器移除并释放当前渲染宿主（Surface/Texture 解除输出），再释放 KernelPlayer/ExoPlayer。
- 保持上一轮已修复的无条件释放渲染视图；进度保存和状态派发仍只在 hadActiveState 时执行。
- 提交：`8317dbb34881433db115e2829d665fd4649e10fc`。

### 当前结论
- 旋转配置声明和直播方向策略在静态代码中没有发现明确冲突；不应继续通过叠加方向锁来试错。
- 渲染释放顺序已统一，但这不等于已修复用户报告的旋转重启。必须结合已有 PID/生命周期日志与 Surface bind/detach 日志确认故障层级。
- `EngineTextureRenderView.onSurfaceTextureDestroyed()` 返回 false，表示应用保留 SurfaceTexture 所有权；当前实现通过后续重新挂接及显式 release 管理它。该路径需要 Android 真机重复旋转、切换 TextureView/SurfaceView 模式测试，静态审查不能证明系统行为完全正确。

## 验证状态更新
- 已完成：Manifest、Activity 生命周期、PlaybackEngine 挂摘、BasePlayerView、SurfaceView/TextureView 和 ExoPlayer 输出解除顺序的静态交叉审查。
- 未完成：Gradle 编译/测试、Android 真机连续旋转、渲染模式切换、快速切频道压力测试。
- 未生成 APK、未发布 Release。


## 第四轮：TextureView 旋转诊断增强
- 为 `EngineTextureRenderView` 的 SurfaceTexture available、sizeChanged、destroyed 回调增加诊断日志。
- 日志记录 SurfaceTexture identity、尺寸、是否已有播放器、是否复用保留的 Texture，以及 destroyed 回调是否返回 false。
- 目的：与 LivePlayActivity 的 PID/onDestroy/onCreate 日志、PlayerEngine 的 surface bind/detach 日志对齐，区分 Activity 重建、SurfaceTexture 重建和播放器内核重建。
- 提交：`f287495b7f5bbe9d7f074e6fedbebdb794e7d706`。
- 日志只增加诊断，不改变 SurfaceTexture 所有权及回调返回值；仍需真机旋转验证日志时序。


## 第五轮：快速重播竞态与 TextureView 释放后回调
### 发现一：重新播放时旧渲染宿主解绑顺序
- `BasePlayerView.startPlay()` 原先先释放旧 KernelPlayer，再由 `addDisplay()` 移除旧 RenderView；RenderView 内部的 detach 因而可能发生在旧内核 release 之后。
- `addDisplay()` 还会对当前 `kernelPlayer` 执行 detach，但当前 kernelPlayer 可能已经是新建内核，实际旧渲染宿主应自行解除它所绑定的旧内核。
- 新增 `releaseActiveRenderView()` 统一执行从容器移除、调用 RenderView.release、清空引用；startPlay 在释放旧内核前调用，addDisplay 和 release 也复用此方法，移除错误的“对当前内核额外 detach”步骤。
- 提交：`f78332a39eece6c76343f5cf222446ec06f8f628`。

### 发现二：异步读取历史进度可能覆盖新播放请求
- `startPlay()` 只有在 ProgressSink 非空时才递增 `startPlaySeq`。如果旧播放请求正在异步读取历史进度，随后新播放请求没有 ProgressSink，旧请求可能仍通过序号检查并调用 `startPrepare()`。
- 现在每次进入 `startPlay()` 都先递增序号；`release()` 同样递增序号，使释放前未完成的进度读取结果失效。
- 新播放请求无论是否读取历史进度，都会淘汰此前请求。
- 提交：`00a4e8b85049bbbc72cb8579240f5715e038a935`。

### 发现三：TextureView 释放后仍可能接收回调
- `EngineSurfaceRenderView` 原有 `released` 标志保护迟到的 Surface 回调；`EngineTextureRenderView` 原先没有等效状态，release 后仍可能收到 SurfaceTexture 回调并重新创建 Surface/持有 Texture。
- 为 TextureRenderView 增加幂等 released 标志；release 时先标记退役、解除 SurfaceTextureListener、解除播放器输出并清理 ready listener，再释放 Surface/SurfaceTexture。
- attach、refresh、输出尺寸、回调入口均增加退役检查；迟到的 available 回调会释放交付的 SurfaceTexture，不再创建新输出。
- 提交：`e3bbc5d539fc7341a17eb129f36f394a8acad927`。

### 验证状态
- 已静态回读三个修改文件，确认旧渲染宿主先于旧内核释放、播放请求序号在每次 startPlay 和 release 时失效、TextureView release 幂等并关闭后续回调入口。
- 尚未运行 Gradle 编译/单元测试；未在设备上执行快速切换、释放后回调和连续旋转测试。上述修改仍需设备验证，不能据此宣称黑屏或旋转问题已解决。



## 横屏直播 UI 美化（平板横屏分支）
- 将横屏播放器左上角频道列表入口改为带菜单图标的胶囊按钮，并与状态栏安全区保持间距；右上角 EPG/设置按钮统一放入同一工具区。
- 将底部频道信息由较松散的大块信息卡改为紧凑的半透明信息面板：频道号、频道名、直播/回看状态、线路、当前节目、下一节目和可选时间/网速分层呈现，并限制最大宽度，避免宽屏信息横向铺满。
- 横屏频道抽屉改为响应式宽度（约屏幕 38%，限制 280–420dp），增加标题、当前频道和频道数量头部，圆角及阴影统一；抽屉背景透明度调整为 24%，并保留抽屉打开时播放器区域整体高斯模糊，列表文字维持黑边白字以保证清晰度。
- 增加「频道列表」中英文及繁体中文资源。
- 本次只调整横屏 Compose UI，不改播放器内核、频道选择逻辑、手势和 Activity 方向策略。尚未运行 Gradle 编译或设备视觉回归；需在不同尺寸平板横屏下检查抽屉宽度、系统栏避让、触控区域与信息卡遮挡。
