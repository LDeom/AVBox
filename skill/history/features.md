# 功能迭代记录

## 2026-11-19 平板横屏直播沉浸式布局

- 新增平板横屏判据：`smallestScreenWidthDp >= 600` 且当前窗口为横向。
- 平板横屏下视频铺满直播主画面，频道信息以半透明浮层呈现；频道列表改为默认隐藏的右侧抽屉，支持遮罩点击关闭、返回键关闭和选台后收起。
- 平板横屏视频单击改为显隐播放器控制浮层，不触发手机式全屏方向切换；EPG 与直播设置继续复用既有入口和面板。
- 手机与平板竖屏继续使用原有布局；新增抽屉可见状态由 `LivePlayViewModel` 管理，不改播放器内核、直播源解析或 EPG 业务。
- 当前仅完成远程源码修改；尚未运行本地 Gradle 构建、单元测试与门禁脚本，平板真机验证待进行。

## 2026-11-19 直播页亮度与音量手势区域修正

- 保留既有播放器控制器的上下滑手势实现与「禁用手势控制」开关；按手势起始点 `e1.x` 相对播放器控制器自身宽度的左右半区判定目标，左侧调亮度、右侧调音量。
- 不再按首次滚动事件的当前位置 `e2.x` 或设备屏幕宽度判区，避免拖动跨过屏幕中线后目标控制不稳定，也兼容非全屏播放器容器。
- 未新增 Compose 手势层，不改变单击、长按、左右快滑切台及频道抽屉边缘滑动逻辑。
- 仅完成源码修改，未运行 Gradle 构建、单元测试、门禁脚本或真机验证。


## 2026-11-19 直播旋转生命周期诊断与频道列表磨砂效果

- 直播 Activity 增加 PID、创建/销毁及配置变化日志，用于区分 Activity 重建与应用进程重启。
- Activity 因配置变化销毁时不再调用 `PlaybackService.exitLive()` 结束共享直播会话；正常退出仍执行原有清理。
- 平板横屏频道抽屉采用低不透明度背景，打开抽屉时对播放器视频层施加 18dp 模糊，列表文字保持正常不透明度。
- 尚未运行 Gradle 构建或真机验证；需重点确认旋转行为及不同 Android 版本的模糊表现。
## 2026-11-19 内置点播源替换

- 将首次启动内置点播订阅替换为 `LDeom/tvbox` 仓库 `audit/repair-sources-2026-11` 分支的 10 个配置：小洒、JSM、JS、饭太硬、电视、0827、367、0826、0825、0821。
- 内置目录版本升至 2；从旧目录版本 1 升级时仅移除上一版仓库预置的点播 URL，再合并新列表；保留用户自行添加的其他点播订阅及直播订阅，并按 URL 去重。
- 10 个配置均已读取并通过 JSON 语法解析，站点条目数依次为 117、163、297、49、164、33、106、54、135、86；尚未逐站点调用 API 验证影视数据，也未验证全部播放链路。
- 仅完成源码与文档更新，未运行 Gradle 构建或真机验证。

## 2026-11-20 内置点播源收敛为单一多仓接口

- 内置点播源只保留 `niubihu1/tvbox` 的 `自用仓库.txt` 多仓接口，文件当前包含 22 个候选仓库入口；配置格式经 AVBox 多仓接口识别逻辑确认。
- 内置目录版本升至 3；从旧版本升级时删除此前由仓库预置的 16 个历史点播 URL 和上一版的 10 个点播 URL，再加入新的单一多仓接口。
- 不清理用户自行添加的其他点播订阅；保留 Clun IPTV 内置直播源及用户已有直播订阅。
- 仅完成远程源码与文档更新，未运行 Gradle 构建、自动化测试或真机验证；22 个下游仓库未逐个测速或验证播放。

## 2026-11-20 直播选台列表对比度与旋转生命周期加固
- 平板横屏频道抽屉中的分组标题、频道号和频道名称统一为白色字形并叠加黑色描边，增强亮/暗视频背景上的辨识度。
- 抽屉背景加强磨砂效果：播放器底层高斯模糊由 18dp 提升至 30dp，列表面板使用更明显的半透明深色底；模糊作用于整个全屏播放器底层，列表面板完整覆盖其上。
- LivePlayActivity 仅在真正结束且非配置变化时调用 `PlaybackService.exitLive()`；非 finishing 的 Activity 销毁不再主动终止共享直播会话，补充 onStop / onPause / 配置变化日志。
- LivePlayActivity 的配置变化声明补充 `uiMode|colorMode`。
- 旋转重启问题尚不能宣称已彻底修复：代码已增加会话保护和生命周期诊断，但仍需在用户设备验证；若进程 PID 变化或发生崩溃，需结合 Logcat 确认根因。
- 仅完成远程源码修改，未运行 Gradle 构建、自动化测试或真机验证，未生成 APK。

## 2026-11-20 直播亮度与音量手势恢复
- 修复 `ComposeLiveController` 手势播放状态判断：原控制器的 `videoView` 从未绑定，导致手势始终可能被判定为不可用；现在直接维护播放器回调提供的 `PlayState`。
- 上下滑方向识别改为累计位移超过系统 touch slop 后再锁定，减少起始横向抖动造成的误判；仍按手势起点相对播放器控制器宽度的左右半区区分左侧亮度、右侧音量。
- 保留系统边缘区域避让、用户手势禁用开关及原有左右快滑切台逻辑。
- 仅完成远程源码修改和静态内容核对，未运行 Gradle 构建、自动化测试或真机验证。


## 2026-11-22 配置解析条目容错第一批
- 站点解析对 sites 缺失或非数组返回空结果；逐条验证站点对象、key/type/api 基本类型与非空值，单条格式错误不再中断后续站点。
- 直播分组名称解析和 hosts 解析对非对象、非字符串元素做防御处理。
- 配置应用层解析线路时逐条校验 name/url，并隔离坏条目；规则应用逐条异常隔离，广告列表仅接收字符串。
- ConfigParserTest 增补异常站点、异常直播分组、hosts 与 parse 线路数据测试。
- 已提交远程源码；未运行 Gradle 构建、单元测试或门禁脚本，仍需本地验证。

## 2026-11-22 配置加载协程取消加固
- VOD 与直播配置加载协程注册取消回调，取消时主动取消对应网络请求；Executor 中排队的任务在协程已取消时跳过。
- 回调仅在 continuation 仍活跃时恢复，降低页面/调用方取消后回调恢复已取消 continuation 的风险。
- 仅提交源码修改，未运行 Gradle、门禁或真机验证；仍需测试快速切源、取消后重试及缓存回退。


## 2026-11-22 阶段2 TXT订阅 JSON容错第一批
- TXT订阅 JSON归一化跳过非对象组与非对象频道；渠道URL列表仅接受字符串项，避免坏项中断整份订阅。
- 直接解析归一化结果时，非对象组/频道与类型不正确的urls条目同样隔离跳过。
- 新增 `TxtSubscribeCompatTest` 覆盖坏组、坏频道、无效/重复URL及旧版 `channel` 字段。
- 尚未执行Gradle测试或真机验证。


## 2026-11-22 阶段2 TXT重复频道合并修复
- 修复 TXT/JSON 订阅映射输出阶段同组同名频道重复出现时，后条频道 URL 覆盖前条 URL 的问题；现在合并并去重 URL，保留首次顺序。
- 新增单元测试覆盖同名频道跨条目合并 URL 的场景。
- 未运行 Gradle、门禁或真机验证。


## 2026-11-22 第3阶段 JS、Jar与路径审查首轮
- 定位并审查 SpiderLoader、JarLoader、JsLoader、JsSpider及QuickJS UriUtil入口。
- 记录JS API Jar缓存下载非原子写入等待验证风险，新增审查记录 `skill/review/stage3-js-jar-path-audit.md`。
- 尚未修复或执行Gradle测试，待审阅Http实现与模块相对路径链路后继续。


## 2026-11-22 第3阶段 JS API Jar缓存加固
- 下载改为临时文件写入，校验HTTP状态、非空响应与可用的MD5，关闭响应资源后再替换缓存；失败清理临时文件并保留有效旧缓存。
- 未执行Gradle测试或设备验证。


## 2026-11-22 第4阶段播放线路审查
- 初步检查 PlayUrlResolver、RetryPolicy、PlaybackAttemptState及HTTP状态测试；报告记录于 `skill/review/stage4-playback-routing-audit.md`。
- 未发现可凭当前局部证据安全直接修改的缺陷，完整解析/嗅探/取消链路仍待深入。未运行Gradle。


## 2026-11-22 第3阶段 JS模块路径第二轮核查
- 追踪 QuickJS `ModuleLoader.moduleNormalizeName` 到 `UriUtil.resolve`，模块加载通过 `FileUtils.loadModule` 获取内容；模块编译/读取失败目前可能返回空模块 bytecode，属于潜在掩盖依赖缺失的兼容行为。
- 已将证据与待验证集成边界追加至 `skill/review/stage3-js-jar-path-audit.md`。未改变兼容加载策略，未执行Gradle。

## 2026-11-22 第5阶段直播旋转生命周期第二轮静态核查
- 复核 LivePlayActivity 的 manifest `configChanges`、`onConfigurationChanged`、`applyFullscreen`、`onDestroy` 与共享 PlaybackService 生命周期，以及 BaseActivity 的方向策略和系统栏处理。
- 当前代码已具备配置变化接管和共享会话保留保护，但旋转是否仍引发 Activity 重建/进程重启尚无真机日志证据，暂不作未经证实的修改。
- 尚未运行Gradle构建/单测或真机验证。


## 2026-11-22 第3阶段 FileUtils模块路径与缓存第三轮
- 追踪 `FileUtils.loadModule` 的特殊模块名映射、URL_JOIN远程模块、MD5缓存、assets/js-lib/file/clan路径处理及 `setCache`/`getCache` 写入恢复链。
- 识别缓存直接覆盖写入的中断风险及assets读取使用available()的通用性限制；目前未验证为实际故障，不改变加载语义。
- 记录更新到 `skill/review/stage3-js-jar-path-audit.md`，等待后续测试基线与针对性修复。


## 2026-11-22 第3阶段缓存写入可靠性修复
- `FileUtils.writeSimple` 采用同目录临时文件、备份及失败恢复策略，避免覆盖缓存前先删除有效旧文件；检查写入长度并清理临时文件。
- 新增 `FileUtilsAtomicWriteTest` 覆盖首次写入及替换路径。
- 尚未运行Gradle/测试或设备验证；持续推进第3阶段。


## 2026-11-22 第3阶段 JS模块缺失回退策略审查
- 审查JS模块内容缺失、404、错误JSON、bytecode解码及编译失败的空模块回退机制；记录模块名日志及兼容性风险，不贸然改变旧站点行为。
- 审查更新至 `skill/review/stage3-js-jar-path-audit.md`。


## 2026-11-22 第3阶段缓存读写链路第二批加固
- `FileUtils.saveCache` 不再先删除旧缓存再直接写入，统一复用临时文件、备份与失败恢复机制。
- `FileUtils.readSimple` 改为完整流读取，避免依赖 `InputStream.available()` 作为文件长度并忽略短读结果。
- `FileUtilsAtomicWriteTest` 增加缓存覆盖写入与完整读取用例。
- 尚未执行 Gradle/单元测试；后续继续审查 Jar/JS 下载缓存与路径加载边界。


## 2026-11-22 第3阶段 Jar缓存下载与内置依赖复制加固
- `JarLoader.download` 下载远程 Jar 时先写入同目录临时文件，检查 HTTP 成功状态、响应体和非空文件，并在完成后通过备份替换缓存。
- `JarLoader.copyAsset` 复制内置 Jar 时采用相同临时文件与备份替换策略，避免复制中断先删除原缓存。
- 下载失败清理临时文件并返回不可加载的临时路径，避免误把旧的过期/校验不符缓存当成本次下载结果加载。
- 尚未运行 Gradle 或真机测试；需重点验证 HTTP 错误、取消下载、文件替换失败和 assets Jar 加载。

- 同批清理已废弃的 Jar 目标文件直接创建/覆盖辅助方法，避免后续误用旧的破坏性写入路径。

## 2026-11-22 第3阶段 JS模块读取与缓存有效期修复
- assets模块读取改为完整流读取并自动关闭，不依赖available()。
- 修复JS远程模块缓存过期后仍返回过期内容的问题；过期缓存立即失效并触发后续重新拉取，损坏缓存字段安全回退。
- 缓存过期时间使用Long和UTF-8格式，规避Int时间溢出及默认字符集差异。
- JS API Jar下载支持线程中断检查，且缓存更新统一使用FileUtils.writeSimple原子写入，保留旧缓存直至新文件完整写入。
- 增加缓存过期边界测试；尚未运行Gradle测试、APK构建或真机验证。

## 2026-11-22 第3阶段缓存编码与旧Jar保留修正
- JS模块缓存读取统一UTF-8，匹配缓存写入编码。
- JS API Jar下载失败只清理临时文件，不再主动删除原有缓存，避免失败刷新破坏已有依赖文件。
- 尚未运行Gradle测试或设备验证。

## 2026-11-22 第3阶段 Spider Jar缓存与取消保护
- SpiderLoader普通Jar下载增加线程中断及加载epoch检查，源切换后尽早停止过期下载。
- 普通Jar与img+Jar缓存替换统一使用FileUtils.writeSimple，避免破坏已有可用Jar缓存。
- 拒绝空Jar并保留旧缓存，失败时清理临时文件。
- 尚未运行Gradle测试、APK构建或真机验证。

## 2026-11-22 第3阶段 Jar缓存与源切换并发协调
- LoadEpoch新增受锁保护的runIfCurrent操作，协调加载取消与缓存文件提交。
- SpiderLoader过期epoch不能提交新Jar缓存，降低快速切换点播源时旧下载覆盖新状态的风险。
- 增加LoadEpoch当前/过期快照测试；尚未运行Gradle或真机验证。

## 2026-11-22 第3阶段缓存并发写入保护
- FileUtils.writeSimple串行化原子写入，避免固定临时文件名在并发缓存任务中产生竞争。
- DRPY二进制缓存标记统一US-ASCII编码。
- 新增多线程并发写入测试，尚未运行Gradle验证。

## 2026-11-22 第3阶段 Jar 生命周期及中断传播
- JarLoader 的清理与加载使用 generation + lifecycleLock 协调；parseJar将初始代际贯穿整个加载过程，防止切换/清理后旧任务重新注册 Jar。
- Proxy、Danmaku 反射方法仅在代际校验通过后写入共享注册表。
- JsLoader遇到线程中断时停止JS API Jar重试并保留中断标记。
- 尚未运行 Gradle 测试或真机验证。

## 2026-11-22 第3阶段 QuickJS 任务取消优化
- JsSpider 的同步等待、JS调用及转换调用在超时/线程中断时显式取消对应 Future；中断时恢复中断标记。
- 处理 Executor 拒绝提交的异常，避免调用路径异常外溢。
- 尚未运行 Gradle 或真机测试；native QuickJS 执行中断能力仍待验证。

## 2026-11-22 第3阶段 JsLoader 代际同步
- JsLoader.clear() 与 Spider/Class 缓存写入统一使用 lifecycleLock；旧代际 JS API Jar 和 Spider 创建任务不能在清理后重新注册缓存。
- generation 显式贯穿 JS API Jar 下载/类加载及 Spider 创建后的注册。
- 尚未运行 Gradle 或真机验证。

## 2026-11-22 第3阶段 QuickJS 定时回调引用释放
- 修复 Global.setTimeout 中 JSFunction.hold() 与 Timer.cancel() 的资源不平衡：销毁时释放所有未触发回调。
- 使用独立 Timer entry 跟踪，支持同一函数多次定时调度；锁定 timer 回调提交与 Context 销毁顺序，避免销毁后回调访问 QuickJS。
- 未运行 Gradle/设备验证。

## 2026-11-22 第3阶段 Async 与 HTTP 回调销毁安全
- Async.Result 加入 first-completion-wins 原子状态和可见性保证，新增 AsyncResultTest。
- HttpGate.clear 改为通知队列中每个请求取消，避免未执行 _stream 的 JS resolve/reject hold 引用泄漏；新增 clear 测试。
- Global 的 HTTP/stream 回调派发、请求入队与 destroy 通过 callbackLock 协调；销毁后阻止 QuickJS 回调触碰已销毁 Context，并关闭竞态中的流响应。
- 未运行 Gradle/设备验证。

## 2026-11-22 第3阶段 JsLoader缓存代际及HTTP队列竞态
- JsLoader缓存读取、recentKey更新、Jar缓存替换均与clearGeneration/lifecycleLock协调，防止返回退役Spider、读取已清空类缓存或旧下载覆盖缓存。
- Global迟到HTTP回调在销毁后不会再启动HttpGate排队请求。
- 未运行 Gradle/设备验证。

- 补全 stream HTTP 成功回调的 HttpGate.finish()，避免 in-flight 计数不释放导致后续请求队列停滞。

## 2026-11-22 第3阶段 HTTP 回调引用与队列关闭修复
- _http complete JSFunction 由 HttpReply 持有并在完成/失败/销毁时一次性释放。
- Spider 销毁时取消专属 HTTP 请求标签；迟到回调不会访问已销毁 Context。
- StreamReply hold 部分失败回滚，release 幂等。
- HttpGate 执行器关闭时逐项失败待处理请求，不再静默丢弃；新增关闭状态测试。
- 未运行 Gradle/设备验证。

## 2026-11-22 第3阶段 QuickJS任务队列与Context销毁顺序
- JsSpider 用 taskLock 串行化任务提交和 destroy，销毁后拒绝新任务，避免旧调用任务排在Context teardown之后。
- 新增 Global.stopAccepting，在 JsSpider 排入Context teardown前停止HTTP、stream、timer回调入队；Global.destroy在JS单线程执行器内完成资源清理。
- Global清理增加独立幂等状态，stopAccepting与destroy分离。
- 未运行 Gradle/设备验证。


## 2026-11-22 第3阶段 JS模块 URI 空引用边界修复
- 修复 UriUtil.resolve 在模块引用为空字符串或 null 时访问空字符串首字符导致越界的问题。
- 空引用按 URI 解析语义返回基础模块地址并移除 fragment，保留 query；正常相对路径解析保持原逻辑。
- 新增 UriUtilTest 覆盖空字符串、null 和普通相对模块路径。
- 尚未运行 Gradle 测试或真机验证。


## 2026-11-22 第3阶段 clan:// 模块路径边界修复
- 将 clan://host/path 转换逻辑抽离为可测试的纯路径方法；对缺少主机或路径分隔符的无效地址提前拒绝，避免 substring 越界后静默丢失模块。
- 保持有效 clan://host/ 空路径及原有本地/远程文件映射语义。
- 新增 FileUtilsModulePathTest 覆盖正常路径、无效路径和空路径。
- 尚未运行 Gradle 测试或真机验证。


## 2026-11-22 第3阶段 file:// 模块 URI 规范化
- 抽离 fileModulePath，精确移除 file:// scheme 和可选前导斜杠，不再对整个 URI 执行连续宽泛替换。
- 兼容 file:///path 与 file://path，拒绝空路径及非 file:// 地址；本地代理地址拼接规则保持不变。
- 扩展 FileUtilsModulePathTest 覆盖有效及无效 URI。
- 尚未运行 Gradle 测试或真机验证。


## 2026-11-22 第3阶段 assets 与 js/lib 路径兼容
- 增加 assetsModulePath 对 assets:// 空路径及 scheme 的校验，保留路径原文，不做未经验证的 URL 解码。
- 修复 loadModule 对已带 js/lib/ 前缀路径重复拼接目录的问题；统一通过 jsLibAssetPath 处理路径。
- 新增单元测试覆盖中文资源路径、嵌套路径及无效路径。
- 尚未运行 Gradle 或真机测试。


## 2026-11-22 第3阶段 JS 初始化超时清理安全
- JS Spider 初始化超过等待时限时不再只取消任务后返回半初始化实例，而是抛出初始化失败，阻止 JsLoader 缓存无效 Spider。
- 初始化失败时停止 Global 接收回调，并将 Global 与 QuickJSContext 清理排入同一单线程 Executor，避免从构造调用线程并发销毁仍在执行的 Context。
- 尚未运行 Gradle、QuickJS native 压力测试或真机验证。


## 2026-11-22 第3阶段 JS 初始化中断传播补充
- 初始化等待被中断时取消 Future、恢复线程中断标记并向上传播，触发同线程队列清理，避免吞掉调用方中断状态。


## 2026-11-22 第3阶段 JS Spider 初始化失败识别
- 根 JS API 模块缺失/无效、模块执行异常或未创建 Spider 对象时不再返回半初始化实例，统一抛出初始化失败并触发安全清理，防止 JsLoader 缓存不可用 Spider。
- 依赖模块缺失的空模块兼容策略暂时保留，待集成测试后再评估。
- 尚未运行 Gradle 或真实配置回归测试。


## 2026-11-22 第3阶段 JS 模块响应判定测试
- 抽离纯 Kotlin JS 模块内容错误响应判定，使用 Locale.ROOT 保证语言环境无关。
- 新增测试覆盖空内容、HTML错误页、404/Not Found、JSON 404、BOM及正常 JS/JSON内容。
- 尚未执行 Gradle；未完成真实 QuickJS ModuleLoader 和 AssetManager 集成验证。


## 2026-11-22 第3阶段 JS 模块错误响应识别补充
- JS 模块响应识别支持带空白的 JSON 404 code 字段，并增加系统 Locale（土耳其语）回归测试。
- 尚未执行 Gradle，新增测试结果待 CI/本地构建验证。


## 2026-11-22 第3阶段 JS 依赖回退诊断
- 为 JS ModuleLoader 的空模块回退增加明确原因日志，区分缺失/错误响应、DRPY/BB bytecode解码失败及 JS 编译失败；保持原有回退语义，避免未经源兼容回归直接改变配置行为。
- 新增 JsModuleContent.invalidReason 分类测试。
- 尚未运行 Gradle、QuickJS native 或真机测试。


## 2026-11-22 第3阶段模板依赖与相对路径回归
- preloadTemplate 增加模板内容预检与无效原因日志；模板缺失时不再继续执行空模块导入，保持非致命兼容策略。导入后检查 getMubans 属性是否存在并记录不完整模板诊断。
- 增加相对模块路径测试：多层目录、父级回退、根路径、query/fragment及空引用处理。
- 尚未运行 Gradle、QuickJS native 或真机测试；循环/重复导入需 native 集成测试验证。


## 2026-11-22 第3阶段 Jar Asset 路径修正
- 修复 JarLoader.copyAsset 对路径执行全局字符串替换的问题，改为仅剥离 assets:// 或 assets/ 起始前缀，保留内部同名目录；拒绝空路径及 .. 路径段。
- 内置 Jar 复制循环响应线程中断；增加路径单元测试。
- 尚未运行 Gradle 或 Android 设备测试。


## 2026-11-22 第3阶段 JarLoader 生命周期竞态收敛
- 将 JarLoader 的 live Jar 登记、别名写入、recent/siteJarKeys 更新和 Spider 创建绑定 clearGeneration，避免 clear() 后旧请求重新注册状态或跨代际继续加载。
- 主 Jar recent 更新与 setRecentJarKey 快照使用 lifecycleLock；Jar Spider 初始化监视器采用 try/finally 保证异常路径收尾。
- 尚未运行 Gradle，真实并发 Jar 加载与清理需设备/压力测试验证。


## 2026-11-22 第3阶段 Jar Spider 注册竞态修复
- 修复 JarLoader.getSpider 中 generation 校验与 Spider 缓存写入不原子的窗口；注册和代际校验现由 lifecycleLock 一次完成，旧代际实例不会在 clear 后重新进入缓存。
- 二次缓存命中校验加入 generation 检查；空 live Jar 不再登记。
- 尚未运行 Gradle 或设备压力测试。


## 2026-11-22 第3阶段 Jar 缓存代际与并发写保护
- Jar 下载、clan 下载及 Asset 复制统一携带 clearGeneration，在读取循环和最终缓存替换前检查代际；缓存替换受 lifecycleLock 保护，旧任务不能在 clear 后写入缓存。
- Jar 缓存锁按规范化源 URL 而非带 MD5 后缀的 loader key 计算，避免同一缓存文件的不同 MD5 声明并发覆盖同一个临时文件。
- 尚未运行 Gradle、并发下载测试或 Android 真机测试。


## 2026-11-22 第3阶段 Jar 下载调用点复核
- 补齐 clan:// Jar 下载的 generation 传递，并将 Jar 缓存互斥锁统一到去除 ;md5; 后缀后的源身份，避免同一缓存文件因不同校验后缀并发写入临时文件。
- 已静态回读所有下载、复制和缓存替换调用点；尚未运行 Gradle 编译或设备测试。


## 2026-11-22 第3阶段 Jar 下载失败路径
- JarLoader 的远程下载和 Asset 复制失败现在返回 null，parseJar 不再将失败/部分临时文件传给 DexClassLoader；仅成功完成原子缓存替换的文件才进入 load。
- 尚未运行 Gradle 或设备测试。


## 2026-11-22 第3阶段 Jar Init 初始化失败传播
- 修复 JarLoader.invokeInit() 吞掉 Init.init(Context) 异常后仍将 DexClassLoader 注册为成功的问题。
- 仅当 Jar 不包含可选 Init 类时兼容跳过；若 Init 类存在但方法缺失、签名不兼容、依赖缺失或执行失败，则阻止该 Jar 注册。
- 增加成功/可选缺失诊断日志；尚未运行 Gradle、DexClassLoader 集成测试或真机验证。


## 2026-11-22 第3阶段 已配置 JS API Jar 加载失败阻断
- 当 TVBox/AVBox 配置显式提供 JS API Jar，但下载、校验或 Function/Method 类加载失败时，JsLoader 不再以 null dex 继续创建 Spider，而是返回 SpiderNull 并记录诊断；未配置 Jar 的旧源兼容行为不变。
- 尚未运行 Gradle、真实 Jar 集成或真机测试。


## 2026-11-22 第5阶段播放器稳定性与旋转问题启动
- 按用户要求跳过第4阶段，直接启动第5阶段；首轮静态审查 LivePlayActivity、BaseActivity、AndroidManifest、PlaybackEngine 与播放服务化规范。
- 核实 LivePlayActivity 已声明 orientation 等 configChanges、覆写 BaseActivity 方向策略为空操作，并在非 finishing 的配置变化销毁时保留直播会话；暂未发现应盲目增加方向锁或修改 Manifest 的证据。
- 确认 PlaybackEngine.enterLive/exitLive 会改变播放器内核生命周期，不应从普通方向变化回调重复调用。
- 新增审查报告 skill/review/stage5-player-stability-rotation-audit.md；尚未运行 Gradle、未做真机旋转测试，本轮未修改播放器核心逻辑、未构建 APK。


## 2026-11-22 第5阶段 BasePlayerView 渲染资源释放修复
- 修复 BasePlayerView.release() 仅在 lastReportedPlayState 非 IDLE 时才移除并释放 activeRenderView 的问题。
- 现在无论播放器状态如何，release() 都会移除并释放当前渲染视图、清空引用并关闭 keepScreenOn；进度保存、播放位置复位和状态派发仍保留原有 hadActiveState 条件。
- 尚未运行 Gradle、单元测试或 SurfaceView/TextureView 真机验证。


## 2026-11-22 第5阶段 Surface/Texture 释放顺序修复
- 继续交叉审查 DetailActivity/LivePlayActivity 配置变化声明、直播方向策略、PlaybackEngine 挂摘及 SurfaceView/TextureView 输出生命周期。
- 确认 BasePlayerView.release() 原先先销毁 ExoPlayer，再调用渲染宿主 detachVideoSurface；现调整为先移除并释放渲染宿主、解除视频输出，再释放 KernelPlayer/ExoPlayer，统一资源释放顺序。
- 直播 Activity 已有 PID 与生命周期日志；正常旋转配置声明静态上应由 onConfigurationChanged 处理，但仍需通过真机日志区分 Activity 重建与进程重启。
- 尚未运行 Gradle/设备测试；未构建 APK。


## 2026-11-22 第5阶段 TextureView 旋转诊断增强
- EngineTextureRenderView 增加 SurfaceTexture available、sizeChanged、destroyed 生命周期日志，记录纹理 identity、尺寸、播放器绑定状态、是否复用 retained Texture 及 destroyed 回调返回值。
- 用于与 LivePlayActivity PID/Activity 生命周期及 PlayerEngine surface bind/detach 日志交叉判断旋转时的故障层级。
- 本次仅增加诊断，不改变 SurfaceTexture 所有权和回调行为；尚未执行 Gradle、真机旋转和播放器压力测试。


## 2026-11-22 第5阶段快速重播与 TextureView 生命周期优化
- BasePlayerView 新增统一 releaseActiveRenderView()，确保重新播放时先移除并释放旧渲染宿主，再释放旧 KernelPlayer；addDisplay/release 共用该流程。
- startPlay 每次调用都递增请求序号，release 也递增序号，避免此前异步读取的历史进度结果在新播放请求或播放器释放后迟到并触发 startPrepare。
- EngineTextureRenderView 增加幂等退役标志，释放时先关闭 SurfaceTextureListener 与回调引用，并在 attach/refresh/尺寸/SurfaceTexture 回调中拒绝退役后的工作，避免释放后重建 Surface。
- 本轮已静态回读修改；未运行 Gradle/单元测试/真机压力测试，未构建 APK。


## 2026-11-22 平板横屏直播界面 UI 美化
- 横屏直播入口升级为带图标的半透明胶囊按钮，右上角 EPG/设置按钮统一定位并避开状态栏。
- 底部频道信息卡改为紧凑分层布局，突出频道名称、直播状态与当前节目，下一节目、时间和网速作为次级信息；信息卡宽度增加上限，适配宽屏。
- 频道抽屉改为响应式宽度，增加标题、当前频道名和频道数量；圆角/阴影统一，背景透明度 24%，抽屉打开时保留播放器背景整体高斯模糊，频道列表文字保持黑边白字。
- 新增频道列表标题的简体中文、英文、繁体中文资源。
- 本轮仅改 UI 与资源，不调整播放器和方向逻辑；未运行 Gradle 或真机测试，未构建 APK。


## 2026-11-22 第六阶段多源搜索稳定性优化
- SearchViewModel 为整批搜索保存 searchJob，新搜索开始时取消上一批并发任务；ViewModel 清理时同时取消搜索任务和建议词任务。
- 单个搜索源超时后，如果该源仍处于 Pending，则收敛为 Done + 空结果；避免搜索总任务已结束但来源卡片仍永久显示等待状态。
- 单源普通异常改为隔离处理，不因一个来源失败而取消同批其他来源；CancellationException 仍向上传播。
- 增加超时边界状态保护：如果真实结果已经先到达，不会被超时空结果覆盖。
- 审查报告：`skill/review/stage6-multi-source-search-audit.md`。
- 尚未运行 Gradle 编译、单元测试及真机压力测试，未构建 APK。

- 第二轮审查发现既有 `JsLoader.stopAll()` 会遍历全局 JS Spider 并取消 pending 工作，可能影响搜索之外正在执行的 JS 源请求。因同步爬虫调用不能仅凭协程取消就确认已停止，本轮未直接删除全局取消；已登记为第六阶段后续专项，需将取消粒度安全收敛到单次搜索请求。

- 第六阶段第二轮：新增 `JsLoader.stop(key)`，SearchViewModel 记录上一批实际搜索的 JS 来源 key，在新搜索/页面销毁时只取消这些来源，不再对搜索调用全局 `stopAll()`。当前取消粒度仍是单个 Spider，不等于单次调用隔离。
- 第六阶段第三轮：搜索并发配置统一限制在 1–64，防止损坏/旧配置造成 Semaphore 非法参数；新增边界单元测试。
- 明确保留不同来源的同名影片，不跨源去重，避免隐藏不同源的线路与可用性；新增回归测试。
- 审查报告补充 JS 取消范围、并发数边界及重复影片策略。

- 第六阶段第四轮：搜索源选择和执行统一过滤空白 key、不可搜索源，并按 key 去重，防止空 key 进入 pendingSources 或来源 UI 产生 key 冲突；新增规范化函数与单元测试。
- 深入审查 QuickJS 的 Connect/Global/JsSpider/JsSession 请求链。确认 Spider 级 HTTP tag 被同步请求、异步回调、流式请求及会话共享，简单给 JS 方法调用套临时 tag 不能安全覆盖异步生命周期。因此没有贸然实现不完整的“单次请求取消”，保留来源级取消并在阶段六审查报告记录架构限制与后续设计要求。


## 2026-11-22 第六阶段 JS 来源取消集合生命周期修正
- 搜索批次正常结束后清空当前批次的活动 JS 来源 key，避免之后退出搜索页/下一次搜索误取消搜索结束后启动的同源播放或其他操作。
- 仅当前 token 对应的批次可在 finally 中清理活动集合，防止旧批次清理新批次的来源记录。
- onCleared 先快照并清空待取消 key，再取消搜索任务并执行定向取消，规避协程 finally 竞态。
- 本轮仍未实现 JS Spider 单次调用级取消；搜索进行期间同源并发操作仍可能受来源级取消影响。
- 提交：`9ad7806832b56988494d6559fe92c64caf16ba9c`；尚未运行 Gradle/单测/设备测试，未构建 APK。


## 2026-11-22 第六阶段搜索源首次终态保护
- SearchViewModel 只接受仍处于 Pending 的来源回调；已完成、超时或已接收结果的来源忽略同批次重复/迟到回调，防止结果被覆盖。
- 需回归确认所有搜索 API/Spider 均以单次完整响应为协议；尚未运行 Gradle/单测/设备测试。
- 提交：`dafc19d5d9682aa1bf34e3705fc1ad29bb6cb0b3`。


## 2026-11-22 第七阶段配置中心第一轮：订阅录入规范化
- 新增纯逻辑函数 `normalizedSubscribe`：统一清理订阅名称和 URL 中的 ISO 控制字符、首尾空白；名称清理后为空时回退为 URL；URL 清理后为空则拒绝保存。
- 点播/直播订阅新增、编辑与本地配置导入共用同一规范化入口，避免名称或 URL 中的制表符、换行等控制字符污染以 TAB 分隔的持久化记录。
- 扩展 `ParseSubscribeTest`：覆盖名称/地址控制字符、空名称回退、清理后空 URL 拒绝。
- 代码提交：`c276690438ae2ccbfccdf66ca1f3bf55d269e859`；测试提交：`93bbad81561bc17629c36cb1bb93d91b6d3b37eb`；测试转义修正：`fdffd159f99be7ef73aaa57ff311523abaf2c806`。
- 本轮尚未运行 Gradle 编译或单元测试，未构建 APK。

- 第一轮补充：新增/编辑订阅的重复 URL 判断和活动源编辑后的切换判断统一使用规范化 URL，避免原始输入与实际保存值不一致。提交：`584052de9ce393617ef46fb4c4603ad03bcea42f`。


## 2026-11-22 第七阶段配置中心第二轮：点播切换失败路径审查
- 核实点播订阅切换先持久化新 URL，再使旧内存配置失效并异步加载；新源失败时当前启动流程进入 Boot.Error，但没有恢复旧 URL、旧线路选择及旧配置。
- 配置管理 ViewModel 会在异步加载开始后立即显示新活动 URL，失败路径没有回滚；ConfigLoader 仅尝试新 URL 自身缓存，不会回退到切换前的源。
- 该问题涉及点播/直播跟随关系、多仓线路、内存配置和加载代际；本轮只记录事务回滚边界，未做不完整的 URL 局部回滚。
- 详细审查见 `skill/review/stage7-config-center-audit.md`。尚未运行 Gradle、单元测试或设备验证。


## 2026-11-22 第七阶段配置中心第三轮：本地目录持久授权校验
- 修复 LocalSourceTree.remember 在获取持久 URI 读取授权失败后仍保存目录 URI 的问题；只有系统确认存在精确 URI 的持久读取授权时才登记。
- LocalSourceTree.serves/open 使用前复核授权，发现撤销或历史无授权的记录时清理并跳过，避免把失效目录当作可用依赖目录。
- 新增 LocalSourceTreePermissionTest 覆盖精确授权匹配、无授权及不同 URI。
- 提交：`333dd1fdf73bc0a9c3243c3a63e423742ceeca5d`、`ad441240cfdf977f7fe63ae2164c5fd5c3ce8636`、`2c4094fe9cc8db6f20eb8d17c3251ee456136110`。
- 尚未运行 Gradle/单测或 Android SAF 真机授权撤销测试。


## 2026-11-22 第七阶段配置中心第四轮：本地相对依赖路径校验
- 修复本地配置相对依赖路径校验误拒绝文件名含 `..` 的合法依赖；改为按路径片段拒绝绝对路径、反斜杠、控制字符、空片段以及真正的 `.`/`..` 目录片段。
- 新增测试覆盖双点文件名、父目录穿越、绝对路径和反斜杠路径。
- 提交：`0c13d3fca77166aafda8c468cfebe4560ce8c8cc`、`fdffc2e26716110a3be2c7e4ee882d81467dd7a6`、`fa5eb196d0bad60f54389846c9f1e84695b40fab`。
- 尚未运行 Gradle/单元测试。


## 2026-11-22 第七阶段配置中心第五轮：点播切换事务回滚
- 新增点播切换快照，覆盖点播 URL、直播 URL/跟随关系、点播和直播多仓线路列表及线路来源。
- 新订阅加载失败时恢复旧快照并重新加载旧配置；旧配置恢复成功后才发布 Ready，恢复失败则显示 Error。
- 配置管理页在 Boot.Ready/Boot.Error 均同步活动源快照。
- 通过待提交快照和 generation/锁保护，避免连续快速切换时较旧请求回滚较新选择或清空较新配置。
- 新增 ConfigRepositorySnapshotTest，验证快照对点播/直播线路列表使用独立副本。
- 提交：`3e5f98e820d719418c8cb6b59670e3f266588ddf`、`5baf5c118e1e0099a1c6abdb6608a99a513c47ca`、`dda77250af02396df5083fb8fa60254e12273051`、`d03489abba549c53cd82a76d2b3f9623f3c936ff`、`44673a9cb259475942007f8a6230f540f346bbb7`、`c36b2493e6eb1eadf2bf0c7b67ace1667485241d`、`0ae4607930291e7fce9a455b142ad88257f9e115`、`26517433196a16da2d027d242b57c2d0cd1ab83d`。
- 尚未运行 Gradle/单测或真机验证。


## 2026-11-22 第七阶段配置中心第六轮：直播源切换旧请求污染保护
- 修复 ConfigLoader 在 ViewModel 丢弃旧回调之前就解析旧直播响应、覆盖全局频道组的问题。
- 新增直播请求 generation；新请求使旧请求失效并取消旧网络调用。
- 缓存、网络响应和失败缓存回退统一经过当前 generation + 当前有效 URL 检查。
- ConfigRepository 的直播源切换、点播切换对直播跟随关系的调整、点播切换快照保存/恢复，与直播配置解析使用同一把锁，消除检查与解析之间的切源竞态。
- 协程取消按请求代际执行，避免旧协程取消新请求。
- 新增 LiveConfigRequestGateTest，覆盖有效请求、旧 generation、URL 改变。
- 提交：`9baa708b13c4cc9700012d152e903b41003e2159`、`b074ccb1c39ba9bf976198c64b7173d6b7bb65a2`、`d0f6271994091c6d3210b9d2570bda9b2e036bc5`、`37469f358c25e0ae4e5bf7073e70f9fbcbd77e36`、`a455d0327c28aea2b462f1c1c8dd59ac9f69a111`。
- 尚未运行 Gradle/单测或真机验证。

- 追加网络任务入队保护：旧直播请求即使在尚未创建网络 Call 时也会于实际请求前再次检查 generation/URL，不再因取消时机错过而占用网络执行队列。提交：`7418d4232bf3a54b7f0d3e012f4780c34fd85778`。

- 第七轮：配置中心新增/编辑订阅对话框的保存按钮复用 normalizedSubscribe 校验，避免 UI 允许提交而 ViewModel 清洗后拒绝、对话框却提前关闭。提交：`ec1ead18d2f24a44ad7773b97f27bb95ea00decf`。

- 复核配置切换 generation 保护时，恢复 onApiUrlChanged 耗时日志在配置失效前取起点。提交：`4c1fec8805e3600cec694e9a67ad88f714efd082`。


## 2026-11-22 第八阶段：首页、详情与分类第一轮
- 检查首页、详情页、分类二级页的加载、分页与请求过期保护，并对照 `avbox-mobile-ui-spec.md` §4.1。
- 发现首页分类和分类二级页在快速筛选/重试时可能替换等待回调，但旧请求未取消；列表结果通道没有请求 token，旧请求迟到时可能被当前回调接收。
- SourceViewModel 列表请求改为每次独立结果通道与请求代际，旧请求结果不再转发；新增 cancelList，首页和分类二级页 Loader 释放时取消请求。
- 保留单独的首页推荐列表 Loader，避免列表请求隔离破坏推荐获取。
- 确认详情页已有 detailToken + DetailResponseGuard 保护，本轮不重复实现。
- 新增 SourceListRequestGateTest。
- 提交：`82392a0b898ebc94931bd369165cddc66aadb782`、`719b1aa7ea166b2bd42e79032f81b8bf199762ee`、`0db7657179c5ab6133ef563e8168b123dd68c6a1`、`0c71e0e188d9a70a16eb4a62fa1f265d63d78507`、`b2584da51d811b035e18e56d655fabf2f1371cb5`。
- 尚未运行 Gradle/单测或真机验证。


## 2026-11-22 第八阶段：首页、详情与分类第二轮
- 修复首页同一订阅源刷新/重试时旧分类元数据响应可能覆盖新结果的问题：SourceViewModel.getSort 每次建立独立结果通道与请求代际，取消上一条请求链，只有当前代际可转发。
- 每次元数据请求单独创建首页推荐列表 Loader，避免共享旧请求的结果通道。
- HomeViewModel 清理时取消分类元数据、推荐元数据请求和看门狗。
- 扩展 SourceListRequestGateTest，覆盖排序请求代际。
- 提交：`c40a7837aa9fb8355a1f0c4a8237b1cc442fb422`、`4d7e211b58a31ed5db02972a65e29f8672740e43`、`8e4cabed92158fb4e39514bbda46347dca5d673d`。
- 尚未运行 Gradle/单测或真机验证。

- 回读时补齐 SourceViewModel 独立结果通道所需的 Flow collect 导入。提交：`b93638eb0338d424d8f63a2be1127b6132401168`。


## 2026-11-22 第八阶段：首页、详情与分类第三轮：详情页切源入口前移
- 详情页原“换源”区域位于剧集选择和线路分组之后，用户需要向下滚动才能找到跨源切换入口。
- 将来源切换区域调整到详情主视觉/播放操作之后、剧集与线路分组之前，让当前来源和自动搜索到的候选来源更早出现。
- 保留现有来源搜索、候选源点击、手动切源及失败回退逻辑；本轮只调整页面入口位置，不改变播放和切源事务。
- 代码提交：`32f61c30d66493e8b2c7b5794bd942c7661991f9`。
- 尚未运行 Gradle 编译、单元测试或真机 UI 验证，未构建 APK。


## 2026-11-22 第八阶段：首页、详情与分类第四轮：首页请求超时隔离
- 将首页原先共用的 20 秒全局看门狗拆分：分类/排序元数据请求独立计时；每个分类列表请求在获得并发许可、真正开始后独立计时 20 秒，不再被其他分类请求重置。
- 元数据超时会取消排序与推荐元数据请求，设置加载失败状态并开放重试；自动重试会重新获得独立的 20 秒时间窗口。
- 分类列表超时只取消对应请求代际；请求 ID 防止旧请求的超时处理取消同一分类后来启动的新请求。首屏超时标记该分类失败；分页超时保留已加载内容并提示，不清空列表。
- 页面退出和换源仍沿用既有 Loader 释放/请求取消链路。
- 代码提交：`c29bd9231fee450093873be884e10995c08a0d4f`、`dfd9e05701af094cd84fd57d8eff0961d71abe01`、`99c132b05c4bbfbbd9fe2cd9b5c00271f5ad3f8a`、`92010c6943362a751ec0c65bc9ec4a40dd1c6f58`、`74a8f53db2e68affc91aeeca61261c4569997b91`。
- 尚未运行 Gradle 编译、单元测试或真机验证。


## 2026-11-22 第八阶段：首页、详情与分类第五轮：分类二级页错误恢复
- 分类二级页列表请求增加独立 20 秒超时；首屏超时或接口失败进入明确错误态，可直接重试，不再把请求失败误显示为空列表。
- 分页超时/失败保留已有视频列表，在网格底部显示重试入口；分页加载中显示独立进度提示。
- 对请求捕获导航层级与 Loader 实例，只有当前分类页的请求可以更新状态；返回上一级、重新进入分类或切换筛选后，旧请求结果/超时不会覆盖新页面。
- 提交：`445e3462fb46e2a362644a0f20e558bdb98308d8`、`7c02ec04173cd7d6cd096bdb93f4ba5e951d693c`、`2a20a688681422cb102b5c5044a67ce47805d08a`、`c91d677905274af7648bfa91375fb2e638fb2b76`、`adf98e95804a70c438aa04bd07393edf834293d2`。
- 尚未运行 Gradle 编译、单元测试或真机验证。

- 分类二级页补充空分页终止处理：分页接口返回空数据时收敛最大页数，避免在页数信息缺失时持续请求空页。提交：`4b313d531dbb43b8f04ff94f1f17f55a5b7bf92e`。

- 分类二级页底部重试/加载控件补齐宽度布局导入，修正 Compose 页面依赖。提交：`36b64900e25bdaac4a580e21ae4ae7e68eb18119`。


## 2026-11-22 第八阶段：首页、详情与分类第六轮：分页请求代际与大屏内容宽度
- 分类二级页 Loader 为每次列表请求分配独立 requestId；超时处理仅能取消仍处于 pending 状态且 ID 匹配的请求，避免旧请求超时回调取消同一 Loader 后续重试。
- 列表结果交付、请求取消和 Loader 释放统一清理 pending ID；页面导航层级与 Loader 实例保护继续阻止旧页面回写。
- 详情页内容流增加 1200dp 最大宽度，并在宽屏容器内水平居中；背景和全屏 Hero 不受内容宽度限制，手机尺寸仍按可用宽度铺满。
- 提交：`fb50aade557cdf9b42165ba48db7abda9e10942e`、`3a310f14a251be296b4b03b561425ec451eceacc`、`f742c67d6c67070e4c0bdaf13de871253e8a63ea`。
- 尚未运行 Gradle 编译、单元测试或真机验证。

- 补齐超时与完成回调同时发生时的状态收敛：即使 requestId 已不再 pending（说明结果交付可能先于取消完成），当前页面仍进入超时恢复状态；取消操作本身仍严格按 requestId 匹配，不会触碰后续请求。首页与分类二级页同步修正。提交：`10fdd913fdfadd8a1bcc69cefe359ac0aa96984b`、`bf59830b684eb317b25b1265b7225a34b0349990`。

- 首页分区分页同样增加空页终止：分页接口返回空视频时保留已加载卡片，并将末页收敛到当前页之前，避免 pagecount 缺失或不准确时无限触发后续空页。提交：`0f5973694142a954c7e294cbb1ac81504311c2eb`。


## 2026-11-22 第九阶段：手机/平板适配第一轮：设置内容列宽度
- 复核现有窗口分档：`WindowSize` 已按 600dp/840dp 区分 Compact/Medium/Expanded；首页与分类网格按可用宽度计算列数，详情内容流已限制 1200dp 并居中。
- 发现设置主页、偏好设置、播放设置、主题设置和配置管理页的主要内容列仍随平板宽度无限拉伸，横屏时阅读跨度过大。
- 新增统一 `WindowSize.CONTENT_MAX_WIDTH_DP = 840`；上述五个页面内容列在宽屏中顶部居中、最大 840dp，较窄窗口仍占满可用宽度，顶栏和弹层保持原有宽度行为。
- 代码提交：`ad74ac2a66674e70e99822531886df3df8c4f773`、`1ca396b7eae32d335d1d57821482b8a039b74e50`、`927c2e0c289068e1a509fe62f5949cfa3ce23ce0`、`c94089dfba6edb3c325692609c4d9450a5030d68`、`a27a96e3095892a60bf4999e56e304f37678a6eb`、`8270eb16512aa8bbe5559798060a87654cacf282`、`896560ce82fe3e4956202ec846513e7dc519f944`、`49f5bf9b37251fa8349e2625bdc85b185a8aedf8`、`aace5196ffe7d0ad989ae892bd7710645433d27e`。
- 尚未运行 Gradle 编译、单元测试或真机验证。
