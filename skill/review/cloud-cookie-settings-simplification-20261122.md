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


## 2026-11-22 网盘网页登录 Cookie 复制
- 登录网页增加复制 Cookie 操作，按 provider 允许的 host 逐域提取当前 WebView Cookie，复制时以主机名分段显示；复制动作不保存凭据到本地应用存储。
- 复制与保存分离；复制未发现 Cookie 时显示现有提示，复制成功提示提醒剪贴板敏感信息。
- 新增四语资源。剪贴板内容在用户主动点击后产生，系统剪贴板可能被其他应用读取，使用者需自行清除。
- 未进行真机验证。


# 第4项：云盘 Cookie 与内置浏览器专项优化（桌面交互补充）

## 本轮范围
- 分支：feature/tablet-live-immersive
- 基线：b4fdc7229af5513c4a2481dc26e461ea6738d601
- 用户新增问题：内置浏览器使用电脑/桌面网页 UI 时，鼠标无法拖动页面，缩放操作不方便。
- 用户约束：未经明确允许不构建 APK。

## 代码检查发现
- 当前登录入口为 CloudDriveLoginActivity，使用 Android WebView 和桌面 Chrome User-Agent。
- 原代码虽然开启 setSupportZoom、builtInZoomControls、useWideViewPort 和 loadWithOverviewMode，但隐藏了系统缩放控件；桌面鼠标滚轮和按住鼠标拖动平移没有专门处理。
- Cookie 按 provider 的 host allowlist 收集并写入 CloudCredentialVault；请求注入限定 HTTPS 与精确 host，不能扩大到任意域名。
- 设置页已有独立 Cookie 复制/清除入口，复制入口不放回浏览器内部。

## 本轮实现
- 增加桌面浏览器工具栏：缩小、放大、缩放比例、重置、后退、前进、刷新。
- 增加 DesktopPanWebView：鼠标左键按住拖动页面时执行滚动平移；鼠标滚轮滚动页面；Ctrl+滚轮缩放。
- 保留 WebView 原生双指缩放、JavaScript/DOM Storage、桌面 User-Agent 和现有 HTTP/HTTPS 导航策略。
- 打开水平/垂直滚动条，允许 WebView 获取焦点，提升桌面网页大页面的可操作性。
- Cookie 保存仍由用户主动点击；日志只记录 provider、host、进度和保存状态，不输出 Cookie 内容。

## 日志安全提醒
- 用户上传的运行日志中出现了包含 Cookie、认证 token/auth_key 的请求信息。不要继续传播原始日志；建议在相关网盘退出并重新登录/刷新会话，避免把有效凭据长期留在共享日志中。
- 后续运行日志应统一对 Cookie、Authorization、token、auth_key、签名 URL 和含凭据的请求头做脱敏，不能只依赖导出时脱敏。

## 尚未完成
- 需要继续检查 Cookie 清理是否覆盖 host-only/domain/path 不同范围的 Cookie，及多次登录后的 CookieManager 与加密 Vault 同步。
- 需要逐家核对 provider 登录跳转 host 与真实 JAR 请求 host，当前 allowlist 不能凭猜测扩大。
- 需要检查 Cookie 注入是否覆盖自带 HTTP 客户端的 JAR，以及 WebView 登录空白页的控制台/资源错误。
- 鼠标拖动、滚轮缩放、Ctrl+滚轮和触屏双指缩放均未进行设备验证。

## 验证状态
- 完成源码静态审查与修改。
- 未运行 Gradle 编译、单元测试、静态门禁或真机验证。
- 未构建 APK，未发布 Release。


## 2026-11-23 运行日志凭据脱敏补强
- 发现此前 AppLog.sanitizeLogLine 主要在导出时运行，原始运行日志文件仍可能存储完整请求 URL、Cookie 及认证参数；导出脱敏规则也无法可靠覆盖嵌套 URL 和 Cookie 多键值。
- 调整为写入文件和 Android Log 前即执行脱敏；对包含查询参数的 HTTP(S) URL 整体隐藏 query，避免嵌套签名 URL、auth_key、token 和 URL 编码请求头泄漏。
- Cookie/Set-Cookie 整个字段按逗号边界整体隐藏，避免只隐藏第一个 Cookie 键值而留下同一 Header 后续 Cookie。
- 对 authorization、token、access/refresh token、auth_key、sign/signature、api_key、password、secret 等字段增加覆盖。
- 新增单元测试覆盖多 Cookie 键值与包含 URL 编码 Cookie Header 的签名请求 URL。
- 注意：此前已经写入磁盘的旧日志不会被自动改写；导出时仍会经过新脱敏逻辑。新日志从本次代码运行后才会在写入前脱敏。
- 未运行编译或测试；未构建 APK、未发布 Release。


## 2026-11-23 Cookie 清理与登录空白页诊断补充
- Cookie 清理原先只按 host-only、Path=/ 发送过期 Cookie，不能覆盖显式 Domain 属性的 Cookie；现在对 allowlist host 与其逐级父域分别发送 host-only/Domain 过期指令，并等待每次 setCookie 回调后 flush。
- 清理范围仍严格限制在当前 provider 的 cookieHosts，不调用全局 removeAllCookies，避免清除其他网盘账号会话。
- WebChromeClient 新增 JS Console 错误元数据诊断，仅记录等级、来源 host 和行号，不记录 console 原文，避免脚本意外输出凭据。
- Cookie 路径属性无法通过 CookieManager.getCookie() 读取；当前清理覆盖常见 Path=/，无法保证清除所有自定义 Path Cookie。若仍出现二次登录残留，需要基于 provider 的实际 Cookie 行为继续处理，不能声称已彻底覆盖。
- 用户上传的运行日志没有包含 echo-webview-login 登录过程，因此无法仅凭该日志确认“登录页空白”的具体原因；下一次启用文件日志时可收集新增加的安全诊断项。


## 2026-11-23 桌面缩放状态同步
- 浏览器工具栏缩放、Ctrl+滚轮缩放统一使用 50%–300% 范围的缩放方法。
- 鼠标滚轮缩放后同步更新工具栏比例显示，避免控件显示值与页面实际缩放脱节。
- 此次仍仅做源码修改与远程回读，没有进行设备交互验证。
