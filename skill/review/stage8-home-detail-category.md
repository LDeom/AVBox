# 第八阶段：首页、详情与分类页面审查

## 第一轮：分类列表请求隔离与取消

### 审查范围
- `ui/page/HomeViewModel.kt`
- `ui/page/PartitionListViewModel.kt`
- `sourcedata/SourceViewModel.kt`
- `sourcedata/ListLoader.kt`
- `sourcedata/SourceResultParser.kt`
- `ui/DetailViewModel.kt`
- `ui/DetailScreen.kt`
- `skill/avbox-mobile-ui-spec.md` §4.1

### 发现
- 首页分类和分类二级页都允许在同一个 `SourceViewModel` 上替换等待中的回调，但旧分类请求此前没有被明确取消。
- 例如用户快速更改筛选条件或重试时，旧请求可能晚于新请求返回；由于列表结果通道本身不携带请求标识，旧结果可能被当前回调误认为新请求结果。
- 详情页已有 `detailToken` 和 `DetailResponseGuard`，本轮确认详情响应过期保护已存在，不重复造轮子。

### 修复
- `SourceViewModel.getList` 每次启动列表请求时先取消前一请求链，并为本次请求建立独立的结果通道。
- 只有当前请求代际的结果才会转发到公开的 `listResult`，旧请求的迟到结果无法进入首页/分类页观察者。
- 新增 `cancelList`，在首页分类 Loader 和分类二级页 Loader 释放时取消底层列表请求。
- 保留独立的首页推荐列表 Loader，避免分类列表请求隔离影响首页推荐获取。
- 新增 `SourceListRequestGateTest`，覆盖当前请求通过、旧请求失效。

### 提交
- 列表请求隔离初版：`82392a0b898ebc94931bd369165cddc66aadb782`
- 保留推荐列表 Loader 并补充取消接口：`719b1aa7ea166b2bd42e79032f81b8bf199762ee`
- 首页 Loader 释放时取消请求：`0db7657179c5ab6133ef563e8168b123dd68c6a1`
- 分类二级页 Loader 释放时取消请求：`0c71e0e188d9a70a16eb4a62fa1f265d63d78507`
- 请求代际单元测试：`b2584da51d811b035e18e56d655fabf2f1371cb5`

### 验证与限制
- 已回读最新 `SourceViewModel`、首页/分类 ViewModel 和测试文件，确认列表请求使用独立通道并按代际转发。
- 尚未运行 Gradle 编译或单元测试。
- 尚未进行真机快速切换筛选、连续重试、翻页过程中返回上级、快速换源测试。
- 后续继续审查首页同源刷新时分类/推荐排序请求的旧响应覆盖问题，以及首页/详情/分类页面的平板布局和加载状态。


## 第二轮：同源刷新时分类元数据旧响应保护
### 发现
- 首页分类元数据请求（分类/筛选定义/推荐列表）此前复用单一 `SortLoader` 和单一 `sortResult` 通道。
- 同一订阅源快速刷新或重试时，旧请求可能晚于新请求完成；仅比较 `sourceKey` 无法区分同一源的不同请求。
- 首页 ViewModel 被清理时，之前没有主动取消排序/推荐元数据请求。

### 修复
- 每次 `getSort` 建立独立结果通道和请求代际，旧请求即使迟到也不能转发到当前页面。
- 每次元数据请求取消上一条请求链。
- 推荐列表 Loader 也按当前元数据请求单独创建，避免旧请求与新请求共享结果通道。
- 首页 ViewModel 清理时取消排序和推荐请求，并取消看门狗。
- 扩展请求门禁单测，覆盖排序元数据请求代际。

### 提交
- 排序请求独立通道与代际：`c40a7837aa9fb8355a1f0c4a8237b1cc442fb422`
- 首页清理时取消请求：`4d7e211b58a31ed5db02972a65e29f8672740e43`
- 排序请求代际单测：`8e4cabed92158fb4e39514bbda46347dca5d673d`

### 验证与限制
- 已回读最新 SourceViewModel 和 HomeViewModel，确认分类列表与排序元数据分别使用独立结果通道、代际校验和取消链。
- 尚未运行 Gradle 编译或单元测试。
- 仍需检查排序缓存写入与取消的边界、首页加载看门狗粒度、平板详情布局与分类分页的真机表现。

- 回读时补齐独立结果通道所需的 Flow `collect` 导入。提交：`b93638eb0338d424d8f63a2be1127b6132401168`。
