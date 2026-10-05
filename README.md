# FakeGXZQ

针对证券类 App（`gxzq`）的运行时数据伪造 + 桥通信监控工具链。
本项目为 iOS 版（越狱 dylib + Cloudflare Worker），下为移植到 Android 的大纲。

---

# 移植到 Android · 大纲

## 一、总体思路
- **后端 Worker 完全复用**，不移植 —— 它本身就是跨平台的，Android 端继续请求同一个 `https://gxzq.yugenpu.com/api/config`。
- **两个 iOS dylib → 两个 Android 组件**：`RuntimeFaker`（改写数据）和 `RuntimeMonitor`（抓包记录）。
- **可复用的核心**：Base64 解码 → 解析外层 JSON → 按 `ACTION` 匹配规则 → 用 JSON path 改写 `RESULT` → 重新编码。这段与平台无关，是移植重点。
- **只换“外壳”**：把 `WKWebView` 的 hook 换成 Android `WebView` 的 hook，把悬浮 `UIWindow` 换成 Android 悬浮面板。

## 二、方案选型（先定这个，决定所有代码结构）
- **A. Xposed / LSPosed 模块**（最贴近 dylib 注入语义，运行时 hook，需 root）
- **B. Frida 脚本**（动态注入，迭代最快；root 用 frida-server，免 root 用 gadget 重打包）
- **C. APK 重打包注入**（免 root，但要改包 + 重新签名，易被校验拦截）
- 建议：**A 为主**，B 用于前期摸底桥协议。

## 三、模块大纲

### 1. Cloudflare Worker（保持不变）
- 无需改动；如目标 App 的 Android 版接口域名/结构不同，仅需调整 `/api/config` 的 URL。

### 2. Faker（改写）
- **Hook 点**：`WebView.evaluateJavascript(String, ValueCallback)`、`WebView.loadUrl(String)`（过滤 `javascript:` 前缀）
- **核心流程**（对应 iOS `RFProcessJavaScript`）：提取 `tztCallWebFuctionFromNative(rid, type, base64)` 的第 3 个参数 → 解码 → 取 `ACTION` → 匹配规则 → 改写 `RESULT`（字典/数组两种形态）→ 重编码 → 只替换原 JS 中该段
- **配置**：启动拉取 + 定时刷新 + 手动重载；兼容 `{enabled,rules}` / `{data}` / `{config}` 三种外壳
- **UI/交互**：悬浮面板（状态 + 重载/暂停/关闭）；三指长按显隐；可拖动

### 3. Monitor（记录）
- **Hook 点**：上述两个 JS 执行点 + `WebView.addJavascriptInterface`（记录桥接口）+ 可选 `WebChromeClient.onConsoleMessage`
- **消息模型**：时间戳 / 类别 / 标题 / 副标题 / 详情
- **解析**：TZT 桥解析（Type、Request ID、Base64 解码后的 JSON、`ACTION`、`ERRORNO`、`RESULT` 二次解析）
- **处理与展示**：JSON 美化、敏感字段脱敏（token/cookie/authorization/…）、Base64 解码、列表 + 详情弹窗、可清空/暂停

## 四、iOS → Android 对照表

| iOS (dylib) | Android |
|---|---|
| `WKWebView.evaluateJavaScript:completionHandler:` | `WebView.evaluateJavascript` / `loadUrl("javascript:")` |
| ObjC runtime swizzle | Xposed `XC_MethodHook` |
| 悬浮 `UIWindow` + `RFPassthroughWindow` | `android.R.id.content` 上叠加的 View（免悬浮窗权限） |
| 三指长按 `UILongPressGestureRecognizer` | hook `Activity.dispatchTouchEvent` 统计触点 + 定时器 |
| `NSURLSession` 拉配置 | `HttpURLConnection` + 定时线程 |
| `WKWebView` 的 JS→native 桥 | `@JavascriptInterface` 对象悬挂方法 |

## 五、分阶段实施计划
- **阶段 0 环境**：目标 App 包名、root/LSPosed 设备、Android Studio（当前环境无 Java/SDK，需另配）
- **阶段 1 打通 hook**：先只打印 `evaluateJavascript` 内容，确认桥协议与 iOS 一致
- **阶段 2 移植引擎**：Base64/JSON path/规则匹配/改写，先用本地固定配置验证
- **阶段 3 接入配置**：远程 `/api/config` 拉取与刷新
- **阶段 4 HUD**：悬浮面板 + 三指长按
- **阶段 5 Monitor**：解析/脱敏/列表详情
- **阶段 6 联调**：对照截图，验证数值与 Worker 计算一致

## 六、难点与风险
- **桥的方向与形态未必相同**：iOS 是 native→web；Android 版可能走 `@JavascriptInterface`、URL scheme 或 `onJsPrompt`，需先摸底
- **`ACTION` 匹配依赖解码后外层 JSON**，不是函数第一参数
- **加固/混淆/签名校验**、WebView 多进程、hook 线程安全
- **悬浮窗权限**：用叠加 `content` 方案可规避
- 目标包名/作用域、`WebView` 未被使用时类可能未加载（需重试安装）

## 七、交付物清单
- 一个 LSPosed 模块工程（`Faker`）+ 一个（`Monitor`），共两个 APK
- iOS→Android 对照说明与协议摸底记录
- （可选）Frida 摸底脚本、README/Build 说明
