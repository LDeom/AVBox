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
