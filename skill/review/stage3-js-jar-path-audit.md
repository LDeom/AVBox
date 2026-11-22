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
