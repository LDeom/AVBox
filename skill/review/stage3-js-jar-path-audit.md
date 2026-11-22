# 第3阶段 JS、Jar、依赖与相对路径审查

## 范围
SpiderLoader、JarLoader、JsLoader、JsSpider、QuickJS UriUtil；仅静态审查，未执行Gradle或真机测试。

## 已确认实现
- SpiderLoader分派JS/Python/Jar，Jar下载以临时文件再替换缓存，并有加载代次取消保护。
- JarLoader支持HTTP、assets、file、clan路径，DexClassLoader动态加载；Spider实例通过代次保护与SpiderReaper退役。
- JsLoader的JS API Jar缓存使用文件路径，并建立DexClassLoader；JS spider创建后在代次失效时销毁。
- QuickJS UriUtil已有相对路径、根路径、查询、fragment和dot-segment单元测试。

## 风险/待确认
1. JsLoader.loadJarInternal下载JS API Jar时直接写入目标cache文件，不采用临时文件+原子替换；下载中断可能留下半成品缓存。已有无MD5缓存路径在加载失败后会继续尝试网络刷新，但失败缓存的行为仍需测试。
2. JS API Jar下载流程未在该方法中显式检查HTTP响应成功状态/关闭response对象；实际行为取决于Http.getSync实现。需审阅Http实现后再修复。
3. JarLoader的file路径处理、clan地址转换及relative ext路径依赖宿主FileUtils/Uri约定，尚未完成跨路径验证。
4. JsSpider的模块导入/相对路径实现仍需补齐模块加载器链路审查。
5. SpiderLoader的加载回调、取消与代次竞争需测试快速切换、取消后回调、缓存恢复场景。

## 下一步
优先修复JS API Jar缓存非原子写入，审阅Http.getSync/响应关闭、ModuleLoader与JS相对路径解析；补测试并同步阶段记录。上述风险并不等同于已验证故障。


## 修复进展
- `JsLoader.loadJarInternal` 已加固：临时文件下载、HTTP成功状态/非空体检查、可提供MD5时下载文件校验、临时缓存替换及异常清理。
- 尚未运行Gradle验证；原子替换在Android文件系统语义及回退copy路径仍待测试。


## 第二轮路径链路核查（2026-11-22）
- `JsSpider.createCtx()` 的 QuickJS `ModuleLoader.moduleNormalizeName` 明确通过 `UriUtil.resolve(moduleBaseName,moduleName)` 解析模块导入；`getModuleBytecode` 从 `FileUtils.loadModule(moduleName)` 获取内容，编译失败或缺失内容返回 empty module bytecode。此行为可能掩盖缺失模块（以空模块代替），需要结合上游配置与模块路径用例验证，不在缺少测试基线时直接改变兼容策略。
- `preloadTemplate()` 使用虚拟模块名 `tv_box_template.js` 导入 `模板.js`，相对路径归一化在当前模块 loader 中执行；当前测试已覆盖 UriUtil常见相对、根路径、query、fragment与dot-segment，但尚未覆盖QuickJS模块装载器整体集成。
- 本轮没有发现足够证据安全修改模块路径算法；下一步将针对 FileUtils.loadModule 的路径映射与 assets/cache/file/URL边界补证据及测试。


## 第三轮路径边界核查（2026-11-22）
- `FileUtils.loadModule` 处理模块名称时按优先级映射特殊模块名（gbk.js、模板.js、cat.js），远程URL由 URL_JOIN 匹配并使用MD5名缓存，assets、js/lib、file://、clan://localhost及clan://host路径分别处理。
- 远程模块缓存写入 `setCache` → `writeSimple`，当前写入实现先删旧文件再直接写入，缓存JSON损坏/写入中断的风险可被 `getCache` 捕获后返回空并重新拉取；该风险主要涉及模块缓存恢复，尚未通过故障注入测试确认。
- `asOpen` 使用 `InputStream.available()` 预分配读取并一次读取，针对assets通常可用但并非通用InputStream保证完整数据。其输入仅限assets路径，暂不判定为实故障。
- 目前未实施路径层改动，避免改变URL匹配、缓存约定与本地文件映射语义；建议后续抽离纯逻辑测试及缓存写入故障测试。


## 第三阶段缓存写入修复（2026-11-22）
- `FileUtils.writeSimple` 改为同目录临时文件完整写入后再替换目标；原目标先移至备份，替换失败尝试恢复；异常时清理临时文件并恢复旧文件。
- 新增 `FileUtilsAtomicWriteTest`：验证首次写入、既有文件替换及临时/备份文件清理。
- 当前仅代码静态核对与提交；未执行 Gradle 测试，故替换行为、平台rename语义及故障恢复仍需构建/测试验证。


## 模块缺失容错策略核对（2026-11-22）
- `JsSpider`模块加载器在内容为空、404/Not Found、错误JSON、Base64 bytecode解码失败或编译异常时回退至空模块bytecode；该策略避免QuickJS加载直接崩溃，但会使依赖模块缺失在运行期表现为功能缺失而非明确模块错误。
- 已有空模块日志包含模块名称，可作为定位线索。改变回退策略会影响旧站点兼容，需先建立站点模块加载集成测试，当前不贸然抛出异常。


## 第四轮缓存读写审查（2026-11-22）
- 发现 `saveCache` 仍采用先删除目标文件再直接写入的旧逻辑，与已修复的 `writeSimple` 不一致；现已改为统一调用 `writeSimple`，确保JSON缓存覆盖失败时保留旧文件。
- `readSimple` 原先使用 `available()` 预分配并单次 `read()`，现改为 `FileInputStream.use { readBytes() }`，保证读取完整文件并自动关闭流。
- 原子写入测试增加 `saveCache` 替换及 `readSimple` 大文件完整读取用例。
- 本轮未运行Gradle测试或设备验证；需在后续完整构建中确认。


## 第五轮 Jar依赖缓存边界修复（2026-11-22）
- `JarLoader.download` 原先调用 `create(file)` 先删除目标缓存，再将网络响应直接写入目标；未显式检查 HTTP 状态/空响应，下载失败仍返回目标文件，存在损坏或空 Jar 被后续加载的风险。
- 现改为临时文件下载，校验 HTTP 成功状态、body存在、文件非空，完成后以备份方式替换目标；响应体与流均使用 `use` 关闭。
- `copyAsset` 同步采用临时文件和备份替换，避免内置 Jar 复制失败破坏已有缓存。
- 失败时删除临时文件并返回临时路径，`load` 的文件存在性检查会阻止错误文件进入 DexClassLoader。
- 该改动目前只完成源码静态核对，未运行 Gradle/单测或设备验证；仍需验证取消/中断和不同文件系统的 rename 行为。

- Jar缓存改造后已移除旧 `create(file)` 破坏性覆盖辅助方法，避免后续调用重新引入先删缓存再下载的行为。

## 第六轮 JS依赖与缓存有效期修复（2026-11-22）
- FileUtils.asOpen 改为通过 use { readBytes() } 完整读取并关闭assets输入流，不再把 available() 当作资源总长度。
- 修复远程JS模块缓存过期后仍返回旧data的问题：过期时删除缓存并立即返回空值，交由调用方重新请求；同时对缺失/错误的expires和data字段做安全处理。
- 缓存expires改为Long并使用UTF-8序列化，避免2038年附近的Int溢出及默认字符集差异。
- JS API Jar下载增加线程中断检查；缓存替换统一调用已加固的 FileUtils.writeSimple，避免先删除有效Jar再写入导致下载成功但缓存损坏。
- 新增缓存过期边界单元测试。尚未运行Gradle测试或真机验证。

## 第七轮缓存字符集与失败回滚核查（2026-11-22）
- getCache读取缓存JSON统一使用UTF-8，与setCache写入编码一致，避免依赖设备默认字符集。
- JS API Jar下载/校验失败时不再主动删除现有缓存文件；下载失败只清理临时文件，保留旧缓存以便后续恢复或供旧配置继续使用。
- 仍需运行测试确认文件替换与旧缓存保留行为；当前未执行Gradle或真机验证。

## 第八轮 Spider Jar缓存替换与过期任务中止（2026-11-22）
- SpiderLoader下载Jar时增加线程中断和加载epoch过期检查，避免切换源后继续读取网络响应并进入缓存替换。
- 普通Jar和img+Jar两种下载结果均改用FileUtils.writeSimple进行安全替换，不再先删除旧缓存再rename/copy。
- 下载内容先完整读入并拒绝空文件；替换失败保留原缓存，临时文件由finally清理。
- 尚未运行Gradle测试或真机验证；需要进一步验证取消时序和缓存并发。

## 第九轮 Jar缓存提交与源切换epoch原子协调（2026-11-22）
- LoadEpoch新增runIfCurrent，在同一同步锁下验证快照并执行缓存提交；next同步递增epoch，避免源切换与缓存替换交错。
- SpiderLoader只允许当前epoch提交Jar缓存；过期下载在写入前被拒绝。
- 新增当前epoch执行、过期epoch拒绝两项单元测试。
- 已完成源码与测试用例编写，尚未运行Gradle测试或真机验证。

## 第十轮缓存并发写入保护（2026-11-22）
- FileUtils.writeSimple增加对象级同步，避免多个JS模块/Jar任务并发使用同一目标的固定.tmp/.bak文件时互相覆盖或误删临时文件。
- setCacheByte的DRPY标记固定使用US-ASCII，避免设备默认字符集影响二进制缓存头。
- 新增8个并发写入任务测试，验证最终文件完整匹配其中一次写入且无残留.tmp/.bak。
- 当前仅提交源码和测试；未运行Gradle测试或真机验证。

## 第十一轮 Jar 加载生命周期与取消处理（2026-11-22）
- JarLoader.clear() 的代际递增与运行态清理在同一生命周期锁内执行。
- parseJar() 捕获开始时的 generation，并将同一代际传递至缓存命中、远程下载、assets 和本地 Jar 的激活步骤；清理后到达的旧任务不得重新注册 ClassLoader。
- Jar 反射得到的 Proxy/Danmaku 方法先保存在局部变量，只有通过生命周期代际校验后才写入共享注册表，避免清理与加载交错造成旧方法残留或误删新方法。
- JsLoader 的自定义 JS API Jar 类查找在遇到 InterruptedException 时恢复线程中断标记并停止重试；下载异常路径同样保留中断状态。
- 本轮为静态源码修复；未运行 Gradle 测试、设备验证或动态 Jar 加载测试。

## 第十二轮 QuickJS 任务超时与取消（2026-11-22）
- JsSpider.submitAndWait() 在任务等待超时或调用线程中断时，显式 cancel Future；中断路径恢复线程中断标记。
- JsSpider.call() 与 callConverted() 对执行器拒绝、等待超时和线程中断分别处理；超时/中断会请求取消任务，避免已排队任务在调用方放弃后继续执行。
- 本轮没有改动 QuickJS native Context 的线程归属和销毁顺序；Future.cancel(true) 不能保证中断正在执行的 native QuickJS 代码，后续仍需审查 Async 内部任务及 Context 生命周期。
- 未运行 Gradle 测试、设备验证或 QuickJS native 压力测试。

## 第十三轮 JsLoader 清理与创建竞态（2026-11-22）
- JsLoader.clear() 的代际递增、Spider/Class 缓存清理和 recentKey 重置在 lifecycleLock 内原子执行。
- JS API Jar 的 Class 缓存注册需在同一锁内确认 generation 仍有效，避免清理完成后旧 Jar 加载任务重新写入 classes。
- getSpider() 创建完成后的 Spider 注册同样在 lifecycleLock 内完成代际校验，清理与注册不能交错产生清理后残留的旧 Spider。
- loadClassLoader/loadJarInternal 显式携带创建代际；清理后开始或完成的旧代际加载不再注册 Class 缓存。
- 未运行 Gradle、Android 设备或 JS API Jar 动态集成测试。

## 第十四轮 QuickJS 定时回调资源释放（2026-11-22）
- Global.setTimeout() 原先对 JSFunction 执行 hold()，但 Spider 销毁时 Timer.cancel() 会直接丢弃尚未触发的 TimerTask，未触发任务持有的 JSFunction 无法 release，存在 native 引用泄漏。
- 新增待执行 Timer entry 集合和 timerLock；每次 setTimeout 创建独立 entry，支持同一 JSFunction 被重复调度时分别平衡 hold/release。
- Global.destroy() 幂等关闭定时器，并在锁内取消/释放全部尚未提交的回调。
- TimerTask 从待执行集合移除并提交至 JS 单线程 Executor 的过程与 destroy 同步，保证已提交回调排在 JsSpider 后续 Context 销毁任务之前；销毁后回调不会再执行 JS。
- 处理 delay 缺失、负数、Timer.schedule 异常及 Executor 拒绝提交时的引用释放。
- 未运行 Gradle、QuickJS native 测试或设备验证。

## 第十五轮 Async 结果幂等与 HTTP/QuickJS 销毁竞态（2026-11-22）
- Async.Result 原先允许多次 complete/completeExceptionally 覆盖结果；Promise then/catch 或异常竞争时可能迟到结果覆盖先完成结果。现在以 AtomicBoolean 保证 first-completion-wins，结果字段 volatile，新增成功/失败/null 三类单测。
- HttpGate.clear() 原先直接清空待处理请求，不调用 Request.fail；_stream 请求的 StreamReply 已 hold resolve/reject，导致回调引用不能释放。现在 clear 委托 cancelPending，逐项通知取消。
- 新增 HttpGate.clear 的待处理回调取消测试。
- Global 的异步 HTTP/stream 回调现在通过 callbackLock 与 destroy 串行化：已提交回调排在 JsSpider Context 销毁任务之前；销毁后不再提交/执行QuickJS回调；stream response 在销毁竞态中关闭并释放回调引用。
- _http/_stream 的请求入队与 destroy 同步，防止队列清理后又提交新请求。
- 未运行 Gradle、QuickJS native 测试或设备验证。

## 第十六轮 JsLoader 缓存读取与清理代际同步（2026-11-22）
- 修复 getSpider() 无锁读取 spiders 缓存并返回实例的竞态：recentKey、generation快照与缓存读取统一在 lifecycleLock 内完成；创建锁内的二次缓存读取也验证generation。
- loadJarInternal()读取classes缓存改为 lifecycleLock 内检查代际并读取；所有Jar加载后的类读取通过 cachedClass()再次验证代际。
- 下载JS API Jar后，在 lifecycleLock 内检查generation并安全替换缓存，避免 clear() 后的旧下载覆盖缓存文件。
- proxyInvoke()读取当前Spider时同步读取recentKey对应的实例。
- 进一步将HTTP/stream完成回调中的HttpGate.finish与destroy协调；销毁后先清空待处理请求再释放当前in-flight槽位，避免迟到回调继续启动排队请求。
- 未运行 Gradle、QuickJS native 测试或设备验证。

- 复核修正：stream onResponse 在未销毁分支仍需释放 HttpGate in-flight 槽位，已补回 finish()，保持队列持续调度；销毁分支则先清队列再释放槽位。

## 第十七轮 HTTP 回调引用释放与队列关闭（2026-11-22）
- _http() 从 options 获取 complete JSFunction 后新增 HttpReply：持有引用直到回调完成、请求入队失败、HTTP失败或 Spider销毁；通过一次性投递和一次性释放避免重复回调/重复release。
- HTTP成功/失败回调在销毁后释放回调引用，不再向已销毁 QuickJS Context 派发任务。
- Global.destroy() 现在取消该 Spider 专属 httpTag 的活动请求，促使在途请求结束并释放持有的 JSFunction。
- StreamReply 增加部分 hold 失败时回滚已持有引用，并使 release 幂等，避免重复释放。
- HttpGate.finish() 在 Executor 已关闭时不再静默清空待处理请求，而是逐个调用 fail("executor shutdown")；shutdown 状态在 gate lock 内读取。
- 新增 HttpGateTest.finish_failsQueuedRequestsWhenExecutorIsShutdown。
- 未运行 Gradle、QuickJS native 测试或设备验证。

## 第十八轮 QuickJS Context 销毁与任务队列提交顺序（2026-11-22）
- JsSpider 新增 taskLock，统一保护普通 Runnable/Callable、同步等待任务、Async 调用任务的提交与销毁状态检查。
- destroy() 在 taskLock 内原子设置 destroyed 并排入 Context teardown；任何后续任务提交均被拒绝，避免“调用线程先通过 destroyed 检查、销毁任务先入队、旧任务后入队并在 Context 销毁后执行”的竞态。
- Global.stopAccepting() 将回调停止接收与资源清理拆开：JsSpider.destroy() 在排入 teardown 前先调用 stopAccepting()；该方法分别同步 callbackLock 与 timerLock，阻止 HTTP/stream/timer 在 teardown 后再向单线程 Executor 排入 QuickJS 任务。
- Global.destroy() 使用独立 cleanupStarted 保证清理仅执行一次；在 executor 所属线程中释放定时器、HTTP、stream及Context关联资源。
- 该顺序保证已排入任务先于 teardown 执行，销毁标记后不再产生新 QuickJS 任务；已排入回调检测 destroyed 后跳过 JS 调用并释放持有引用。
- 未运行 Gradle、QuickJS native 测试或设备验证。


## 第十九轮 JS 模块 URI 空引用边界修复（2026-11-22）
- 静态追踪发现 UriUtil.resolve() 在 referenceUri 为空字符串或 null 时，会继续访问 reference[0]，触发越界异常。
- 按 URI 相对引用语义修复：空引用返回基础 URI，并移除基础 URI 的 fragment；保留 query 与原路径。
- 新增回归测试覆盖空字符串、null，以及正常相对模块路径，确保常规模块目录解析不受影响。
- 尚未运行 Gradle 单元测试；测试结果待执行验证。


## 第二十轮 clan:// 模块路径边界修复（2026-11-22）
- FileUtils.loadModule 原先解析 clan://host/path 时直接使用 substring(0, indexOf('/'))；当地址缺少路径分隔符时 indexOf 返回 -1，抛出异常后被外层捕获并静默返回 null。
- 抽离 clanModuleUrl 做纯路径转换，并在访问 host/path 前验证主机非空且存在路径分隔符；无效地址明确记录日志，不再依赖异常控制流程。
- 保留 clan://host/ 的空文件路径行为及原有 http://host/file/path 映射。
- 新增 FileUtilsModulePathTest 覆盖正常主机路径、缺少主机/分隔符及空路径。
- 尚未运行 Gradle 测试；网络端点及真实设备本地代理仍待集成验证。


## 第二十一轮 file:// 模块 URI 规范化（2026-11-22）
- 将 file:// 模块地址解析抽离为 fileModulePath，避免对完整 URI 连续执行宽泛字符串替换。
- 兼容 file:///path 与 file://path 两种既有输入形式；拒绝空路径及非 file:// 地址。
- loadModule 使用规范化后的相对路径拼接本地代理地址，保持原有 LocalAddress/file/ 路由方式。
- 新增测试覆盖两种有效 URI 形式及空/非目标 URI。
- 代码结构已回读检查；尚未运行 Gradle 测试，也未验证设备端 LocalAddress 路由。


## 第二十二轮 assets:// 与 js/lib 模块路径处理（2026-11-22）
- assetsModulePath 显式验证 assets:// scheme 与非空资源路径，路径主体保持原样，不擅自 URL decode，避免破坏中文名或合法特殊字符。
- loadModule 新增对已带 js/lib/ 前缀路径的直接打开分支，避免再次拼接 js/lib/ 造成重复目录。
- jsLibAssetPath 统一处理已带前缀路径与 js/lib 根目录下的裸文件名；带其他目录的裸路径不误拼接。
- 新增测试覆盖嵌套资源路径、中文路径、空路径和已带前缀的 js/lib 路径。
- 代码已回读；尚未执行 Gradle 测试。实际 AssetManager 文件存在性及设备端 QuickJS 相对模块解析仍待集成验证。


## 第二十三轮 JS 初始化超时与 Context 清理线程安全（2026-11-22）
- 发现 initializeJS() 等待初始化 Future 超时后只 cancel 并记录日志，却不向构造方抛出失败；JsSpider 构造可能继续返回一个尚未初始化完成的实例，随后被 JsLoader 缓存。
- 现将初始化超时作为构造失败向上传递，避免返回无效 Spider。
- 构造失败时不再从调用方线程直接销毁 QuickJSContext；先停止 Global 接收新回调，再将 Global/Context 清理排入 JsSpider 单线程 Executor，随后关闭 Executor。这样若 native QuickJS 调用暂时不响应 interrupt，清理会排在其后，不与正在使用 Context 的线程并发销毁。
- 若清理任务无法排队则记录并关闭 Executor；native 调用是否及时响应中断仍需真机/压力测试确认。
- 本轮静态检查已完成，未运行 Gradle、QuickJS native 测试或设备验证。


## 初始化等待线程中断处理补充
- 补充处理 initializeJS() 等待 Future 时的 InterruptedException：取消初始化任务、恢复当前线程中断标记并向构造方传播异常；构造失败清理继续通过 QuickJS 单线程 Executor 排队执行。


## JS Spider 根模块初始化失败检测补充
- 发现根 API 模块缺失/内容无效、模块执行异常或未创建预期 Spider 对象时，初始化任务此前会正常返回 null，导致构造函数认为初始化成功，JsLoader 可能缓存一个不可用 Spider。
- 现将根 API 模块缺失、bytecode/模块执行异常及 Spider 对象缺失提升为初始化异常；与上一轮超时修复配合，统一走构造失败路径和 QuickJS 单线程清理，避免无效实例进入缓存。
- 依赖模块的空模块回退策略暂时保留，避免未经集成测试破坏旧站点兼容；根模块与依赖模块区分处理。
- 尚未运行 Gradle 或真实配置集成测试；需用正常 JS API、无效 API、语法错误 API 和缺少导出对象的 API 做回归。


## 第二十四轮 JS 模块响应内容判定可测试化（2026-11-22）
- 将 JS 模块响应内容判定抽离为 internal 的纯 Kotlin helper：JsModuleContent.isInvalid，避免测试必须创建 QuickJSContext。
- 错误响应大小写转换固定使用 Locale.ROOT，避免设备系统语言影响 HTML/404/Not Found 判断。
- 新增 JsModuleContentTest，覆盖 null/空白、HTML错误页、大小写混合、404、Not Found、JSON 404、BOM，以及正常 JS/JSON 文本不误判。
- 该测试仅覆盖响应文本分类，不代表真实网络、QuickJS ModuleLoader、循环导入或 AssetManager 集成已经通过。
- 未运行 Gradle；当前环境没有可用的仓库本地构建执行结果，需后续在 GitHub Actions/本地 Android SDK 环境执行。


## 模块错误响应识别边界补充
- JSON 404 响应识别补充空白容忍：支持 code 字段前后存在空格的 JSON 错误体，避免只识别无空格的固定前缀。
- 新增测试覆盖 JSON code=404 的空白格式及土耳其语系统 Locale，验证错误响应判断固定使用 Locale.ROOT。
- 保留仅识别明确错误体的策略，避免把普通业务 JSON 中出现的数字 404 误判为下载失败。
- 仍未运行 Gradle；以上测试已提交但尚未执行。


## 第二十五轮 JS 依赖模块空模块回退诊断（2026-11-22）
- 审查发现 ModuleLoader 对缺失依赖、HTML/404响应、DRPY/BB bytecode解码失败、普通JS语法编译失败均可能返回同一份空模块字节码。该兼容回退可能避免部分可选依赖导致整个源初始化失败，但也会掩盖缺失依赖和语法错误，表现为导入模块缺少导出成员。
- 本轮不改变既有回退行为，避免未经真实 TVBox 源回归就改成严格失败。改为为每种回退记录明确 reason：empty、html-error-response、json-404-response、http-404-response、not-found-response、drpy-bytecode-decode-empty/failed、bb-bytecode-decode-failed、javascript-compile-failed。
- 抽离 JsModuleContent.invalidReason，保留 isInvalid 作为兼容入口；使用 Locale.ROOT。
- 新增测试验证回退原因分类，并覆盖空白 JSON 404、设备 Locale 与有效 JS/JSON不误判。
- 仍需真实源集成验证：确认哪些依赖是可选、哪些缺失必须让根模块失败；后续再决定对模板依赖和必需模块采用严格还是兼容策略。
- 未运行 Gradle、QuickJS native 或真机测试。


## 第二十六轮 模板模块预检与相对路径回归用例（2026-11-22）
- preloadTemplate() 之前直接 evaluate import 模板.js，加载失败时只记异常；模块加载器又可能以空模块回退，造成模板缺失未被清晰归因。
- 现增加模板资源预检：FileUtils.loadModule("模板.js") 返回空/HTML/404等无效内容时，记录 echo-preloadTemplate-unavailable 及分类原因，并跳过模板赋值；不将模板缺失升级为根 Spider 初始化失败，保持旧源兼容。
- 模板导入执行后检查 globalThis.getMubans 是否存在；缺失时记录 incomplete 诊断。该检查仅判断属性存在，不假设所有历史模板导出结构完全一致。
- 扩充 FileUtilsModulePathTest，覆盖多层相对路径、父级回退、根相对路径、query/fragment、空引用保留query并去掉fragment。
- 模板预检会额外读取一次模板资源；常见 assets 路径读取成本低，但真实资源加载与 QuickJS 导入仍需设备验证。
- QuickJS 的循环导入/重复导入缓存由底层模块引擎管理，本仓库没有 native 可执行测试入口；本轮仅覆盖 URI 规范化，不宣称循环导入验证通过。
- 未运行 Gradle、QuickJS native 或真机测试。


## 第二十七轮 Jar Asset 路径与取消检查（2026-11-22）
- JarLoader.copyAsset 原先通过连续 String.replace 删除 "assets://" 和所有 "assets/" 子串，可能误改合法嵌套目录名，例如 nested/assets/source.jar 会变成 nested/source.jar。
- 新增 assetJarPath，分别兼容 assets://path 与旧式 assets/path，只移除开头前缀；拒绝空路径、非目标前缀及包含 .. 路径段的输入，避免目录逃逸。
- copyAsset 读取循环增加线程中断检查，避免切源/取消后仍持续复制较大的内置 Jar。
- 新增测试覆盖两种前缀、嵌套 assets 目录、空路径、非法前缀及父目录回退。
- 仍未运行 Gradle；AssetManager 实际路径、Jar 解包/类加载和中断行为需设备验证。


## 第二十八轮 JarLoader 清理代际与初始化监视收尾（2026-11-22）
- 复核 JarLoader 后发现 parseJar 的 aliases 写入、loadLiveJar 的 liveJars/recent 更新，以及 getSpider 的 recent/siteJarKeys 更新分散在生命周期锁之外；clear() 期间可能发生旧任务重新写入状态或把旧代际请求带入新代际。
- loadLiveJar 现在捕获 clearGeneration，并在 lifecycleLock 下登记 live key；解析和最终 recent 更新均使用同一代际，clear 后的旧任务不再继续注册。
- parseJar 增加内部代际参数重载，公开入口在调用时捕获当前代际；aliases 写入在 lifecycleLock 下再次校验代际。getSpider 原子取得 generation 与缓存快照，并将 Jar 解析绑定到该 generation。
- 主 Jar load 的 recent 更新也绑定加载代际；setRecentJarKey 的 recent/loader 快照在生命周期锁内取得。
- Jar Spider 初始化监视器改为 try/finally，初始化异常时也会结束监视，避免监视状态残留。
- 上述均为静态并发审查及代码修复；尚无并发单元测试覆盖真实 DexClassLoader/Jar加载清理竞态，未运行 Gradle/native/真机测试。


## 第二十九轮 Jar Spider 缓存注册原子性补充（2026-11-22）
- 继续追踪发现 getSpider() 初始化 Jar Spider 后，generation 检查与 spiders[spKey] 写入原先分成两个步骤；clear() 若恰好发生在两者之间，旧 Spider 仍可能在清理后重新进入缓存。
- 现将 generation 校验与 Spider 缓存注册放入同一个 lifecycleLock 临界区；若注册时已跨代际，则通过 SpiderReaper.retire 回收新建实例并返回 SpiderNull。
- 二次缓存检查也在 lifecycleLock 下同时验证 generation；空 live Jar 输入不再创建无意义 key 或登记 live 状态。
- 该修复针对静态可确认的竞态；尚无可运行的 JarLoader 并发单元测试，仍需真实 Android/DexClassLoader 压力测试。


## 第三十轮 Jar 下载代际提交与同源缓存串行化（2026-11-22）
- 进一步检查发现 JarLoader 的网络下载/Asset 复制会先替换磁盘缓存，再调用 load() 校验 clearGeneration；若 clear() 在下载期间发生，旧任务仍可能在磁盘写入旧 Jar。
- download/copyAsset/replaceCacheFile 现传递同一 generation；开始、读取循环及最终替换前均检查代际，最终备份替换在 lifecycleLock 内完成，过期任务不能在 clear 后提交缓存。
- clan:// Jar 下载路径也统一传递 generation。
- 另发现不同 ;md5; 后缀会生成不同 loader key，却指向同一 fileForJar(source) 缓存文件，原先按 loader key 加锁无法防止同一 .tmp 文件并发写入。parseJar 现按规范化 source 的缓存身份使用 jar-cache 前缀锁，同一物理缓存目标串行处理。
- 尚未运行 Gradle、网络并发测试或真机测试；需覆盖下载中 clear、同 URL 不同 MD5 后缀、下载失败旧缓存保留及并发复制。


## 第三十轮后续校验补充（2026-11-22）
- 复核全部调用点后补齐 clanToAddress(source) 的下载调用，确保 clan:// Jar 路径也传递原始 generation；所有 download/copyAsset/replaceCacheFile 调用均使用代际参数。
- 将 parseJar 的互斥锁改为基于去除 ;md5; 后缀后的源身份（jar-cache: + MD5(source)），避免相同物理缓存文件因不同 MD5 声明使用不同锁并发写同一 .tmp。
- md5 远程校验结果需要回填，因此 checksum 局部变量保持可变；已回读确认所有下载/复制/替换调用均使用新签名。
- 上述为静态源码回读，没有运行 Kotlin/Gradle 编译；最终编译仍待 CI/本地 Android SDK 验证。


## 第三十一轮 Jar 下载失败回退路径修正（2026-11-22）
- 交叉审查发现 JarLoader.download/copyAsset 在异常时返回临时文件对象；parseJar 随后仍将其交给 load/DexClassLoader。临时文件若留下非空但不完整内容，可能产生二次类加载错误，并模糊原始下载失败原因。
- 两个方法现在以 nullable File 表示成功/失败，异常路径清理临时文件并返回 null；parseJar 仅在获得非空成功文件时调用 load。原有目标缓存不会因失败临时文件而被替代。
- 该变更缩短了失败路径并防止把部分下载的 Jar 当作有效输入；仍需验证 HTTP中断、空响应、AssetManager异常、旧缓存保留等运行场景。
- 未运行 Gradle、Android/DexClassLoader 测试或真机验证。


## 第三十二轮 Jar Init 初始化失败传播修复（2026-11-22）
- 复核发现 JarLoader.invokeInit() 将所有异常捕获后仅记录日志，load() 仍会继续注册 DexClassLoader；当 Jar 内存在 Init 类但 init(Context) 执行失败时，Jar 会被错误标记为加载成功，后续 Spider/代理功能才暴露异常。
- 现仅将 Init 类本身不存在视为可选扩展并兼容跳过；若类存在但方法缺失、方法签名不兼容、依赖类缺失或 init 执行抛错，则异常向上传播到 load()，阻止该 Jar 注册为可用加载器。
- 成功执行 Init 时记录明确诊断日志。
- 该策略保留无 Init 类的旧 Jar 兼容性，同时避免吞掉真实初始化失败。仍需以包含/不包含 Init 的真实 Jar 验证行为；未运行 Gradle、DexClassLoader 或设备测试。
