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
