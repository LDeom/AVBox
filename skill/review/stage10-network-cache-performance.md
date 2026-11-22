# 第十阶段：网络、缓存与性能审查

> 分支：`feature/tablet-live-immersive`
> 日期：2026-11-22
> 范围：HTTP await 层、OkHttp 响应生命周期、DoH 磁盘缓存、播放器磁盘缓存、TMDB 元数据缓存与 Room 通用缓存。
> 本文是源码静态审查和本轮局部修复记录，不代表构建、单测或真机性能验证。

## 第一轮发现与处理

### 中：协程取消竞态可能延迟释放 HTTP 响应体

- 位置：`app/src/main/java/com/github/avbox/osc/util/net/Http.kt` 的 OkHttp `Callback.onResponse`。
- 原因：OkHttp 回调已经交付 `Response`，但协程在 continuation 调度恢复前取消时，调用方可能不会进入 `response.use`；原先直接 `cont.resume(response)` 未为这个窗口提供资源清理。
- 处理：通过 `Http.resumeResponse` 注册 continuation 取消回调，在响应尚未交付给调用方时关闭响应体。正常恢复仍由原有 `response.use` 负责关闭。
- 回归测试：新增 `get_closesResponseWhenCancelledAfterDelivery`，直接覆盖响应恢复前取消的时序。
- 影响范围：只影响取消竞态下的响应资源回收，不改变请求重试、HTTP 状态码判定、请求头和响应内容。

## 其余缓存/网络现状（本轮只读核查）

- 默认 OkHttp 客户端未启用通用 HTTP 磁盘缓存；DoH 专用客户端配置了 100 MiB 磁盘缓存。需结合 DoH 响应命中率、缓存目录实际占用和设备空间再决定是否调整，不凭容量数字直接缩减。
- 播放器缓存由 Media3 `SimpleCache` + LRU 驱逐器管理，大小由用户配置控制，默认值 512 MiB，最小读取值 128 MiB；缓存实例首次创建后大小固定，运行中修改设置不会重建现有实例。需验证设置提示是否明确“重启/重建后生效”。
- TMDB 缓存同时使用最多 512 项的访问顺序内存 LRU 和 KV 持久化缓存，并有负缓存 TTL、请求合并和并发上限；应继续检查大对象内存占用与过期项清理策略。
- Room 通用缓存的 `clearAllProgress` 会读取全部缓存行并反序列化后筛选 Long 类型进度项；在没有完整盘点缓存 key 契约前，不改表结构或序列化格式。

## 验证边界

- 已新增单元测试代码，但本轮尚未运行 Gradle 编译或单测。
- 未运行设备弱网、断网恢复、DNS 切换、磁盘缓存命中/淘汰和长时间播放测试。
- 未构建 APK，未发布 Release。
- 后续优先：核对播放器缓存设置生效时机、TMDB 缓存内存/持久化边界，再决定是否做进一步代码调整。
