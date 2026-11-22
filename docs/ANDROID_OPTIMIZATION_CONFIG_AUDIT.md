# Android 优化：配置解析审计（第一阶段）

> 审计范围：`feature/tablet-live-immersive` 分支。本文是源码静态审计，不等于编译、运行或真机验证。未修改 main 分支应用代码。

## 初步能力盘点

| 领域 | 当前源码入口/已有能力 | 结论/待验证 |
|---|---|---|
| TVBox JSON | sites、spider、jarCache、flags、parses、lives、hosts、rules、doh、ads、logo 等结构入口 | 对真实配置样本的兼容仍需测试 |
| 点播站点 | key/name/type/api/searchable/quickSearch/changeable/filterable/playUrl/ext/jar/playerType/categories/timeout/click/style/icon/hide/indexs/danmaku/header 等字段入口 | 部分字段及异常值组合需验证 |
| 多仓集合 | 识别无 sites、含 urls 数组的集合形式；条目可为对象或字符串，支持 name/url 或 api | 递归加载、汇总、跨仓去重、故障隔离、搜索聚合需确认 |
| 直播 | JSON 与 M3U 入口；提取 headers、catchup、hosts、EPG相关标记 | M3U/JSON端到端播放及 catchup模板需测试 |
| 解析线路 | parses 列表 name/url/type/ext 入口 | 各解析类型兼容性需测试 |
| Spider/Jar/JS | 加载、缓存及下载相关逻辑存在 | 依赖路径、缓存一致性、网络失败、兼容性需测试 |
| 相对资源路径 | 处理相对资源路径的入口 | 多层相对路径、不同基址、编码与本地/远程混合需测试 |
| 安全与网络 | header过滤、host规则、DoH、广告配置处理入口 | 极端输入与跨源行为需测试 |
| 配置加载 | 缓存、refresh、取消旧请求及旧请求防污染机制入口 | 并发切换、失败回滚及状态一致性需测试 |

## 主要风险

1. JSON部分字段采取安全转换，另一些字段直接转换为数组/对象；异常结构可能使整个配置加载失败。
2. 多仓集合解析不代表完整实现了多仓递归加载、站点合并、重复项处理和失败隔离。
3. 对实际TVBox源的兼容性不能仅靠字段存在来判定；必须按真实样本、响应内容及播放结果测试。
4. 网络/下载任务应验证超时、取消、重试、缓存与配置切换间的竞态。
5. 当前结论来自静态阅读；尚未完成编译、设备运行、点播/直播/EPG/回看实测。

## 下一步测试顺序

1. 多仓集合：字符串条目、对象条目、空条目、坏URL、多个仓、重复仓、部分失败。
2. JSON容错：缺失字段、null、错误类型、空数组、畸形 JSON；确认错误隔离而非整份配置崩溃。
3. 资源路径：相对路径、绝对URL、带查询参数、嵌套目录、跨源。
4. 点播 API：响应为空、异常结构、搜索/分类/详情/播放链路。
5. 解析线路与Spider/Jar：依赖下载、缓存、失败重试及配置切换。
6. 直播：M3U与JSON、headers、EPG、catchup、异常频道数据。
7. 运行时验证：低内存、弱网、横竖屏、任务取消、UI错误反馈。

## 改动策略

- 第一批优先做配置加载健壮性与多仓行为核实/修复。
- 先补测试和小批改动，静态核验后再集中构建。
- 不修改 main 分支应用代码；不擅自发布 Release 或生成 APK。
