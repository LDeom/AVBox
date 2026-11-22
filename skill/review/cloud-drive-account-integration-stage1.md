# 网盘账号接入审查记录：第一轮

## 范围
用户已确认网盘账号统一在设置内管理，并同意光鸭采用手机号短信验证；光鸭仅用于个人账号文件浏览和 AVBox 应用内部播放，不实现公共外链分享或转发。

## 现状核对
- 当前仓库未发现可复用的阿里云盘、夸克、百度、115、UC、光鸭账号适配器。
- 现有 Cookie 工具属于 JS 爬虫运行时，不是网盘账号管理与安全凭据存储层。
- MMKV 默认不加密，且其写入为异步调度；凭据存储因此独立使用 Android Keystore + AtomicFile，不把凭据放入 MMKV。
- 各平台认证方式不同，不能用一个 Cookie 字段或统一登录协议覆盖全部平台。
- 光鸭短信协议有变动迹象，登录协议必须隔离为可替换适配器；其上游驱动文档还明确警告不要用于多人分享、视频外链播放等分发用途。

## 第一轮实施
- 建立 AES-GCM 加密信封与 Android Keystore 凭据保险库。
- 每家网盘凭据使用独立应用私有目录 AtomicFile 与独立 Keystore alias；同步写入 AES-GCM 密文，退出单家账号不影响其他 provider。
- 凭据不写日志；provider ID 做格式约束；认证数据读取失败时抛出错误，不静默生成新密钥覆盖旧凭据。
- 新增纯 JVM 加密封装测试，覆盖加密往返、随机 IV 与密文篡改认证失败。凭据文件使用 AtomicFile，写入后执行 fd.sync()，并在失败时回滚临时文件。

## 风险与边界
- Android Keystore 的设备/ROM 兼容、密钥失效与应用数据备份恢复仍需真机验证。
- 当前保险库尚未接入任何平台认证或 UI，不能称为网盘已登录或视频源已接通。
- 光鸭短信 API 可能发生服务端调整，必须在实现前重新核对当期接口；不复制 AList 等 AGPL/GPL 驱动代码。
- 夸克、百度、UC 的网页 Cookie 接口属于非公开接口，必须限制 Cookie 注入域名和请求用途；115、阿里云盘应优先采用官方授权/令牌流程。
- 播放桥接只允许 AVBox 内部个人访问，不生成公开分享链接、不做公共代理。

## 第一轮验证状态
- 已完成代码回读；尚未运行 Gradle 编译、单元测试、静态门禁或 Android Keystore 真机测试。
- 未构建 APK、未发布 Release。



## 第二轮补充：光鸭短信登录与文件浏览
- 新增独立 GuangYaPanClient，按 account.guangyapan.com 与 api.guangyapan.com 两个服务域分别发起认证和文件列表请求。
- 登录流程：初始化 CAPTCHA token；请求 verification_id；提交短信码取得 verification_token；提交 signin 换取 access_token/refresh_token。
- 账号检查通过 /v1/user/me；令牌过期后通过 /v1/auth/token 刷新，使用同步锁串行刷新并保存服务端轮换后的 refresh_token。
- 设置页提供六家 provider 状态展示、光鸭短信登录、账号状态检查、退出登录、根目录与子目录浏览。
- 光鸭遇到官方 CAPTCHA challenge 时直接提示使用官方验证，不尝试绕过。SMS 端点目前仍属于可能调整的服务端协议，必须真机联调确认。
- 凭据文件新增云备份与设备迁移排除规则；AtomicFile 读取改为 openRead 恢复备份。
- 已接入应用内播放桥：文件列表视频项构造 guangya:// 内部标识，PlaybackStarter 通过 provider 换取短时 HTTPS 地址并复用现有 ExoPlayer/PlaybackEngine；云盘会话禁用外部投屏，不生成分享链接。
- 云盘会话采用专用 source key；未注册 SourceBean 时不覆盖 ApiConfig 当前点播源，退出页面时取消待完成的云盘地址解析任务。
- 增加 URL 日志脱敏，防止签名参数、路径和 userinfo 进入诊断日志。
- 网盘请求使用独立标准 TLS 客户端，不复用通用网络客户端的 trust-all SSL 配置。
- 当前文件列表与下载地址接口尚未联调；代码尚未编译，不能确认接口字段、服务端成功码、目录列表、令牌刷新、签名 URL 有效期和 ExoPlayer 播放行为在实际账号上可用。
