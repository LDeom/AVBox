# TVBox 配置解析兼容性审计（阶段 1）

## 审计范围
- `ConfigParser.kt`：sites、header、depot、live-setting、hosts、相对路径与 URL 解析。
- `ConfigApplier.kt`：rules、ads、parses、host-rule 应用。
- `DefaultConfig.kt`：安全字段读取与列表读取。
- `SourceDepot.kt`：多仓 URL / API / name 字段读取。
- `ApiConfig.kt`：解析入口、配置重置、站点映射及规则应用。
- `ConfigParserTest.kt`：已存在的容错与字段测试。
- `ConfigLoader.kt`：缓存、配置拉取与 stale guard 的入口审阅。

## 现有兼容能力
- sites 顺序保留；无效站点条目被独立跳过。
- sites 的 key/api/type 受类型检查；HeaderGuard过滤header。
- depot URLs支持对象中的url/api及纯字符串条目。
- rules、ads、parses字段类型异常时有容错。
- 配置解析入口在切换URL后拒绝旧URL内容。
- 本地/远程、clan/file路径及配置URL处理已有实现。

## 初步兼容性风险（待阶段化验证）
1. 字段类型差异：`DefaultConfig.safeJsonStringList` 对对象或数组的读取逻辑可能不兼容部分变体，数组元素异常可导致列表整体回退为空；需用兼容性矩阵验证。
2. sites字段约束：`key`、`api`、`type` 严格类型验证，非标准但可修复的配置写法可能被跳过；需区分合法TVBox契约与扩展写法，避免宽松转换引入歧义。
3. 多仓TXT、URL合并及重复项策略属于阶段2，当前审计仅确认入口与调用链，未进行完整行为验证。
4. JS/Jar依赖、相对路径与动态加载契约属于阶段3；此轮未做加载器全链验证。
5. API解析、播放器错误诊断属于阶段4，尚未检查完整播放调用链。

## 审计结论
阶段1完成源码入口与兼容性边界盘点，尚非完整回归结论。当前未对TVBox标准契约做破坏性改动；风险项将在后续阶段结合测试逐项处理。未运行Gradle或真机验证。
