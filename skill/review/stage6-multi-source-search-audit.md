# 第六阶段：多源搜索稳定性审查

## 审查范围
- `ui/SearchViewModel.kt`：多源并发调度、请求序号、结果收敛与取消。
- `sourcedata/SearchLoader.kt`：点播源搜索请求与异常/取消传播。
- `api/SearchSourcesState.kt`、`util/SearchSettingsState.kt`：搜索源选择与配置持久化。
- `net/SiteSearch.kt`、`net/SearchHelper.kt`：搜索源筛选与旧选择校验。
- 搜索页结果呈现与搜索源设置面板。

## 本轮确认的既有行为
- 多个可搜索源并发请求，使用 Semaphore 限制并发数。
- 搜索请求带 token，旧 token 的结果会被忽略。
- 支持精确匹配、源选择、结果按到达顺序展示。
- SearchLoader 对 CancellationException 重新抛出，普通请求异常会回传空结果。
- 搜索源选择按当前 API 配置保存，空选集有单独持久化标记。

## 本轮修复
### 1. 单源超时后结果永久 Pending
原实现使用 `withTimeoutOrNull` 包裹请求和结果等待；超时后只结束协程，不会把对应 SourceResult 从 Pending 改为 Done。搜索总任务可能已经结束，但界面仍显示该源未完成。
- 超时后将仍处于 Pending 的该源结果收敛为 Done + 空列表。
- 若真实结果在超时边界先到达，状态检查会保留真实结果，不以空结果覆盖。
- 普通异常也按单源失败处理，不再让一个源的异常取消整个并发搜索批次。

### 2. 快速连续搜索缺少任务级取消
原实现递增 token 并取消底层请求，但未保留整个并发搜索 Job；旧批次中等待 Semaphore 的协程仍可能存活到获得许可后才检查 token。
- 增加 searchJob 引用；发起新搜索前取消旧批次 Job。
- ViewModel 清理时同时取消搜索 Job 与建议词 Job。
- 保留 token 校验作为第二道隔离，避免取消边界上的旧结果写入新搜索。

## 本轮提交
- SearchViewModel 修复：`7718e6fdf857e9dbc2eba43255684336f0532a4c`

## 尚待审查
- 搜索源 key 重复/空值的配置边界。
- searchThreads 的配置范围与运行期切换语义。
- JS Spider 并发调用是否存在跨源共享状态，以及 stopAll 对其他爬虫任务的影响。
- 慢源/坏源、快速连续搜索、切换搜索源与退出页面的设备压力测试。
- 搜索结果重复项合并策略目前未统一；不同源相同影片是否合并需按既有产品行为决定，不能擅自去重。

## 验证状态
- 已回读确认 SearchViewModel 修改内容。
- 尚未运行 Gradle 编译、单元测试或设备压力测试；未构建 APK。
- 本轮仅修复已能从代码确认的超时收尾和任务取消问题，不宣称整体多源搜索已验收。


## 第二轮审查：JS 爬虫取消范围
- `SearchViewModel.search()` 在每次新搜索前调用静态 `JsLoader.stopAll()`。
- `JsLoader.stopAll()` 遍历全局缓存的所有 JS Spider 并调用 `cancelByTag()`；`JsSpider.cancelByTag()` 会取消 QuickJS 的 pending 工作并调用 `Connect.cancelByTag()`。
- 因此取消范围并不天然局限于当前搜索批次：同一时刻其他页面若正在使用 JS 源（例如首页分类、详情或播放解析），也可能受到影响。该风险来自现有全局取消 API，不是本轮新引入。
- 暂不简单删除 `stopAll()`：`SearchLoader.searchFromSpider()` 的爬虫调用在 IO 上执行同步方法，单靠协程 Job 取消不一定能中断底层 JS/网络工作；删除后可能造成旧搜索仍占用 Spider 或与新搜索竞争。
- 正确收敛需要把 Spider 的取消粒度从“全局 / 单 Spider”进一步收敛到“单次搜索请求”，并验证 Connect/QuickJS 的 tag 生命周期；本轮列为第六阶段后续重点，不做未经验证的替换。


## 第二轮修复：JS 搜索取消范围收窄
- 新增 `JsLoader.stop(key)`，只取消指定 key 对应的 JS Spider，不再从搜索页调用全局 `stopAll()`。
- SearchViewModel 记录上一批搜索实际使用的 JS 源 key；下一次搜索或页面销毁时，只对这些旧搜索来源调用定向取消。
- 取消仍通过该 Spider 的既有 `cancelByTag()` 实现，因此范围已从“所有 JS 源”缩小到“上一批搜索涉及的 JS 源”，但仍可能取消同一个源同时发起的其他请求。尚未实现真正的单次搜索调用 tag 隔离。
- 取消范围改为上一批搜索的源集合，而不是新一批选择的源集合，避免搜索源选择变化后旧的未选中 JS 源继续运行。

## 第三轮优化：并发数边界
- 新增 `SearchSettings.normalizeSearchThreads`，将运行期并发数限制在 1–64。
- SearchViewModel 初始化和读取配置时统一使用边界归一化，避免旧配置/异常配置为 0 或负数导致 Semaphore 初始化失败。
- 为并发数下限、默认值、上限及极端整数添加单元测试。

## 重复影片结果策略
- 当前搜索结果按来源分组展示，卡片标识由 sourceKey 与视频 id 组成。
- 不跨来源按片名去重：同名影片可能来自不同源，播放地址、清晰度、集数和可用性可能不同；合并会隐藏可选线路。
- 增加回归测试，确保两个来源的同名影片仍保留为两个来源结果。
- 本轮不做同一来源内部去重，避免在缺少稳定唯一键和源数据约定时误删不同版本/线路。

## 第二、三轮相关提交
- JS Spider 指定来源取消：`2ac317b299de683e63a27e20a3abeaee101be2ca`
- SearchViewModel 上一批 JS 来源跟踪与定向取消：`bd0329ef2acb51f7c0fa2270031d6c75ad8201de`
- 并发数边界归一化：`90844306eeb1444c86c6fee3a5bb5d9d83677c8b`
- SearchViewModel 使用安全并发范围：`408ce04df23d489b7f4228226b06481e8e3a14ac`
- 并发数边界测试：`80e7d34bf51697d203d383a86b8a55006decdeb3`
- 跨来源同名影片保留测试：`ae0c837eac8c7b1b323029bc50e502fe8b7c1831`

## 仍待处理
- 同一 JS 源内搜索与播放等请求仍共享 Spider 级取消范围；若要精确到单次调用，需要设计并验证 QuickJS 执行任务与 OkHttp 请求的关联 tag。
- 需继续检查 sourceKey 重复/空值配置边界、搜索结果流的乱序/重复回调，以及真实设备的连续搜索压力。
- 尚未运行 Gradle 编译、单元测试或设备测试；未构建 APK。


## 第四轮：来源 key 完整性与 JS 请求取消架构结论
- 搜索源列表统一过滤空白 key，并按 key 去重；SearchSourcesState 的展示列表、已选 key 集合与 SearchViewModel 实际执行列表使用一致的有效来源规则。
- 增加来源规范化单元测试：空 key、空白 key、不可搜索源被排除；重复 key 仅保留首个配置项。
- 深入检查 `Connect`、`Global`、`JsSpider`、`JsSession`：
  - 当前 HTTP 请求 tag 由每个 Global/Spider 持有，所有通过该 JS Spider 发起的请求共享 tag。
  - JS 的同步 HTTP、异步 `_http`、Promise stream、openStream 及会话请求分别有不同生命周期；部分异步回调由 Global 的 executor/HttpGate 延后执行。
  - 仅在 JsSpider.call() 外层临时替换 tag，不能保证所有异步回调、后续 timer、session 请求都仍属于原始搜索调用；可能造成漏取消或误取消。
  - SearchLoader 目前通过 Spider 接口调用 `searchContent(key, quick)`，没有请求级取消上下文；简单扩展一个 token 而不贯穿 QuickJS 异步请求生命周期不安全。
- 因此本轮不伪造“单次请求取消”：目前保留按来源 Spider 取消的兼容行为，并明确其同源并发请求可能受影响。下一步若要实现严格请求级取消，需要在 JS API 请求对象、异步回调/流、会话与取消注册表中贯穿 task id，再对全部请求入口做回归测试。
- 本轮没有改变全局 JS HTTP tag，也没有改动 QuickJS 异步执行模型。

## 第四轮提交
- 搜索源空 key/重复 key 过滤：`67f9659ae59abdf928ebb2482b19cdcf97afb6f7`
- SearchViewModel 执行列表 key 防护：`f7bda680b2a8754b38f59295754abe426d71a793`
- 提取来源规范化函数：`deac286951434b3a1e078c24cc84cd72be202d20`
- 来源 key 边界测试：`41086f1f735828dcecf9fad67132ac7d645dcad0`

## 测试状态
- 已添加自动化测试代码，但尚未执行 Gradle 编译或单元测试。
- 尚未进行多 JS 源并发、同源搜索与播放并发、快速连续搜索的设备压力测试。
- 未构建 APK。


## 第六阶段第五轮：JS 来源取消集合生命周期修正
- 发现 SearchViewModel 在搜索批次正常完成后仍保留 activeJsSourceKeys；后续退出搜索页或发起新搜索时，可能对同一 JS 来源发起 Spider 级取消，误伤搜索结束后开始的同源播放/其他操作。
- 搜索 Job 增加 finally 清理：仅当结束的批次仍是当前 token 时清空活动 JS 来源集合，避免旧批次取消后清理新批次的来源记录。
- onCleared 先快照并清空待取消来源，再取消协程和底层搜索，最后按快照执行定向取消，避免协程 finally 与页面清理之间发生集合清空竞态。
- 仍需注意：搜索进行期间同源的其他 JS 操作仍可能被 Spider 级取消；本轮只修复搜索结束后的过期取消集合，不宣称实现单次请求级取消。
- 提交：`9ad7806832b56988494d6559fe92c64caf16ba9c`。
- 尚未运行 Gradle 编译、单元测试或设备压力测试。



## 第六阶段第五轮补充：搜索源结果首次终态保护
- onSearchResult 现在只接受仍处于 Pending 的来源结果；来源已完成、超时收敛或已接收过一次结果后，忽略同 token 的重复/迟到回调。
- 这样每个来源在一个搜索批次内只发生一次 Pending → Done 转换，避免重复回调覆盖先到结果，也避免超时后的迟到响应重新改写结果列表。
- 提交：`dafc19d5d9682aa1bf34e3705fc1ad29bb6cb0b3`。
- 尚未运行测试；需重点回归多页/增量搜索源，确认其协议确实是单次完整响应。

