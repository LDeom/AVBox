# 第3阶段 JS、Jar、依赖与相对路径审查

## 范围
SpiderLoader、JarLoader、JsLoader、JsSpider、QuickJS UriUtil；仅静态审查，未执行Gradle或真机测试。

## 已确认实现
- SpiderLoader分派JS/Python/Jar，Jar下载以临时文件再替换缓存，并有加载代次取消保护。
- JarLoader支持HTTP、assets、file、clan路径，DexClassLoader动态加载；Spider实例通过代次保护与SpiderReaper退役。
- JsLoader的JS API Jar缓存使用文件路径，并建立DexClassLoader；JS spider创建后在代次失效时销毁。
- QuickJS UriUtil已有相对路径、根路径、查询、fragment和dot-segment单元测试。

## 风险/待确认
1. JsLoader.loadJarInternal下载JS API Jar时直接写入目标cache文件，不采用临时文件+原子替换；下载中断可能留下半成品缓存。已有无MD5缓存路径在加载失败后会继续尝试网络刷新，但失败缓存的行为仍需测试。
2. JS API Jar下载流程未在该方法中显式检查HTTP响应成功状态/关闭response对象；实际行为取决于Http.getSync实现。需审阅Http实现后再修复。
3. JarLoader的file路径处理、clan地址转换及relative ext路径依赖宿主FileUtils/Uri约定，尚未完成跨路径验证。
4. JsSpider的模块导入/相对路径实现仍需补齐模块加载器链路审查。
5. SpiderLoader的加载回调、取消与代次竞争需测试快速切换、取消后回调、缓存恢复场景。

## 下一步
优先修复JS API Jar缓存非原子写入，审阅Http.getSync/响应关闭、ModuleLoader与JS相对路径解析；补测试并同步阶段记录。上述风险并不等同于已验证故障。


## 修复进展
- `JsLoader.loadJarInternal` 已加固：临时文件下载、HTTP成功状态/非空体检查、可提供MD5时下载文件校验、临时缓存替换及异常清理。
- 尚未运行Gradle验证；原子替换在Android文件系统语义及回退copy路径仍待测试。


## 第二轮路径链路核查（2026-11-22）
- `JsSpider.createCtx()` 的 QuickJS `ModuleLoader.moduleNormalizeName` 明确通过 `UriUtil.resolve(moduleBaseName,moduleName)` 解析模块导入；`getModuleBytecode` 从 `FileUtils.loadModule(moduleName)` 获取内容，编译失败或缺失内容返回 empty module bytecode。此行为可能掩盖缺失模块（以空模块代替），需要结合上游配置与模块路径用例验证，不在缺少测试基线时直接改变兼容策略。
- `preloadTemplate()` 使用虚拟模块名 `tv_box_template.js` 导入 `模板.js`，相对路径归一化在当前模块 loader 中执行；当前测试已覆盖 UriUtil常见相对、根路径、query、fragment与dot-segment，但尚未覆盖QuickJS模块装载器整体集成。
- 本轮没有发现足够证据安全修改模块路径算法；下一步将针对 FileUtils.loadModule 的路径映射与 assets/cache/file/URL边界补证据及测试。


## 第三轮路径边界核查（2026-11-22）
- `FileUtils.loadModule` 处理模块名称时按优先级映射特殊模块名（gbk.js、模板.js、cat.js），远程URL由 URL_JOIN 匹配并使用MD5名缓存，assets、js/lib、file://、clan://localhost及clan://host路径分别处理。
- 远程模块缓存写入 `setCache` → `writeSimple`，当前写入实现先删旧文件再直接写入，缓存JSON损坏/写入中断的风险可被 `getCache` 捕获后返回空并重新拉取；该风险主要涉及模块缓存恢复，尚未通过故障注入测试确认。
- `asOpen` 使用 `InputStream.available()` 预分配读取并一次读取，针对assets通常可用但并非通用InputStream保证完整数据。其输入仅限assets路径，暂不判定为实故障。
- 目前未实施路径层改动，避免改变URL匹配、缓存约定与本地文件映射语义；建议后续抽离纯逻辑测试及缓存写入故障测试。


## 第三阶段缓存写入修复（2026-11-22）
- `FileUtils.writeSimple` 改为同目录临时文件完整写入后再替换目标；原目标先移至备份，替换失败尝试恢复；异常时清理临时文件并恢复旧文件。
- 新增 `FileUtilsAtomicWriteTest`：验证首次写入、既有文件替换及临时/备份文件清理。
- 当前仅代码静态核对与提交；未执行 Gradle 测试，故替换行为、平台rename语义及故障恢复仍需构建/测试验证。
