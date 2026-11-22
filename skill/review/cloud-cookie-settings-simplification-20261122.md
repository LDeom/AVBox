# 网盘 Cookie 简化与旧专用流程清理审查

## 范围与依据
- 分支：feature/tablet-live-immersive
- 基线：1a84b29f94c256689a4e5d3ed163a516afe70057
- 用户要求：只保留设置内按网盘填写 Cookie 并自动接入；移除此前独立网盘登录、文件浏览和专用播放等多余实现。
- 配置依据：用户提供的 1.json 中实际列出夸克、UC、百度、迅雷、阿里、光鸭、天翼、123 云盘八个 provider，驱动 API 分别为 csp_PanQuark、csp_PanUC、csp_PanBaidu、csp_PanXunlei、csp_PanAli、csp_PanGuangya、csp_PanTianyi、csp_Pan123。配置本身未声明 Cookie 字段，也未内嵌驱动实现；spider 指向外部 JAR，因此不能仅凭配置断言八家驱动都以 Cookie 认证。

## 本轮修改
- 设置 tab 新增「云盘 Cookie 设置」入口；页面按八家 provider 独立保存、覆盖和清除 Cookie。已保存内容不回显，输入值仅保留在当前页面内存。
- 复用 CloudCredentialVault 的 Android Keystore AES-GCM 与 AtomicFile；每个 provider 使用独立凭据 ID。Cookie 不写入 MMKV、普通配置 JSON或运行日志。
- 新增 CloudCookieInjector：仅对 HTTPS 且命中 provider 服务域名 allowlist 的请求注入；合并 Cookie 时保留源请求中的其他键，保存值覆盖同名键。
- JS HTTP 桥接 Connect 接入按目标域名注入。
- TVBox JAR 通用 HTTP 客户端 com.github.catvod.net.OkHttp 增加 CloudCookieNetworkInterceptor，覆盖走宿主 OkHttp 门面的普通 API 请求；type-3 JAR 代理路径另由 ProxyEntry 按来源 API/key 与域名双重校验后注入。
- 新增单元测试覆盖 Cookie 同名覆盖、Set-Cookie 属性过滤、无效输入、provider/domain 隔离及 HTTPS 限制。
- 删除 GuangYaPanClient.kt、CloudDrivePlaybackActivity.kt；设置页移除短信认证、账号状态检查、个人文件浏览和目录导航；PlaybackStarter 移除 guangya:// 地址解析；PlaybackController 移除 cloud: 专用生命周期分支；Manifest 移除专用播放 Activity。
- 同步移除只为光鸭专用播放链路服务的 MediaSources 严格 TLS 标记和独立播放器 TLS client。
- 修正 data_extraction_rules.xml 的 device-transfer 规则，完整排除 cloud_credentials/ 目录。
- 更新四语资源、skill/avbox-mobile-ui-spec.md 与 skill/history/features.md。

## 安全边界与未决项
- Cookie 注入限制在 HTTPS 和网盘服务域名；不按“当前正在播放/点播”全局注入，不向任意站点发送。
- JAR 使用宿主 com.github.catvod.net.OkHttp 的请求可经网络拦截器注入；若某个外部 JAR 自带独立 HTTP 客户端、绕过宿主 OkHttp 门面，则本轮拦截器无法覆盖，需按真实驱动逐项验证。
- 1.json 指向外部 spider JAR，当前仓库未包含八个 csp_Pan 驱动的源码。各驱动可能使用 Cookie、Token、OAuth 或其他认证协议。保存 Cookie 不等于该 provider 已成功登录；Token-only 驱动不会因本功能自动获得 Token。光鸭是否支持 Cookie 需由实际 JAR 请求验证。
- 服务域名 allowlist 是基于各 provider 官方域名空间设置的首轮范围，仍需用真实驱动请求主机名回归；若实际请求使用其他合法域名，应先确认归属后再扩充 allowlist。
- Android Keystore 真机兼容性、各 provider Cookie 有效性、JS/JAR 实际请求注入与播放链路均未在本轮设备上验证。

## 静态核对
- 新设置页只引用 CloudCredentialVault、八家 provider 资源与通用设置组件。
- 专用短信客户端和专用播放 Activity 已删除，Manifest 注册已移除。
- PlaybackStarter 不再包含 guangya:// 解析或云盘专用 Job；PlaybackController 不再保留 cloud: 分支。
- 备份与设备迁移规则均排除 cloud_credentials/。
- 未发现本轮新增代码注释；生产代码保持项目零注释规范。

## 验证状态
- 已完成远程文件回读与静态差异核对。
- 未运行 Gradle 编译、单元测试、静态门禁、JAR 真机联调或 APK 构建；不能宣称本轮代码已通过编译或测试。
- 未构建 APK，未发布 Release。


## 2026-11-22 浏览器登录第一轮补充
- 设置 UI 已改为内置 WebView 登录；保存动作由用户明确触发，不在网页加载完成时自动抓取。
- CookieManager 读取范围仅限每家 provider 的 host allowlist；持久化结构为加密 JSON host map。请求端只向 URL 精确 host 注入对应条目；保留旧版纯 Cookie 字符串读取兼容。
- 首轮入口 URL 采用 provider 官方站点主域名，实际认证可能跳转到其他子域名；若 Cookie 仅存在于未列出的认证 host，保存时会提示未检测到 Cookie。应依据真实 WebView 跳转和 JAR 请求再扩展 allowlist，不能放宽到任意站点。
- 本轮尚未编译、测试或真机验证。不能宣称八家登录均已成功；Token-only 驱动与独立网络栈 JAR 仍需适配。


## 2026-11-22 第二轮清理核对
- 删除 CloudDriveLoginActivity 中从未参与 UI 的 WebView Compose 状态变量、空 onDestroy 覆盖以及默认行为已是拒绝的 onCreateWindow 空实现。
- 将 WebView 实例改由 Activity 生命周期持有；销毁时停止加载、清理并 destroy，保存前调用 CookieManager.flush()。
- 检查 CloudCookieInjector 的 provider 匹配方法仍由 ProxyEntry type-3 路径使用；凭据 vault 的清理/旧凭据兼容逻辑保留，不作未经调用关系证明的删除。
- 本轮构建结果以对应 GitHub Actions run 为准；WebView 登录和真实驱动认证仍需真机验证。


## 2026-11-22 网盘网页登录兼容性修正第一轮
- 登录 WebView 采用桌面 User-Agent、viewport、缩放与兼容混合内容设置，并允许 HTTP/HTTPS 页面内跳转，拒绝其他 scheme。
- 维持 host allowlist Cookie 捕获及加密 Vault；未扩大 Cookie 捕获范围。
- 尚未逐家真机验证，不能确认网页登录、保存和配置中心驱动注入均成功。
