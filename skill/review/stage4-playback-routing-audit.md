# 第4阶段播放线路、解析API与失败诊断初步审查

## 范围
PlayUrlResolver、RetryPolicy、PlaybackAttemptState及相关HTTP状态测试。

## 已核实
- RetryPolicy只对Manifest malformed刷新解析，对HTTP状态异常单独识别，并通过cause链提取HTTP响应码。
- 对本地代理HTTP 500状态的提示进行了抑制，测试覆盖直接源与代理路径的区别。
- PlaybackAttemptState集中记录线路重试、解析刷新、预加载、投屏、ISO及HTTP状态相关标志。

## 待审问题
- PlayUrlResolver多个解析路径、WebView嗅探、JSON解析、超时及请求取消的完整生命周期仍需逐段精查。
- 重试状态字段多且交织，必须结合实际调用路径和测试确定是否存在状态重置遗漏，不基于字段数量推断故障。
- 未发现足以据此修改核心逻辑的明确缺陷；避免盲改。

## 验证
只读审查与已有测试文件检查；没有运行测试/构建或设备验证。
