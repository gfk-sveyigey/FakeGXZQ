# 构建与发布

本工程是 README 里「移植到 Android」大纲的落地实现，产出两个 LSPosed 模块 APK：

| 模块 | 目录 | 对应 iOS 端 | 作用 |
|---|---|---|---|
| Faker | `faker/` | RuntimeFaker | Hook WebView 的 JS 执行点，按配置改写 TZT 桥数据 |
| Monitor | `monitor/` | RuntimeMonitor | Hook 同样的点位 + 桥接口 + 控制台，解析并展示 |
| 共用引擎 | `core/` | 与平台无关的核心逻辑 | Base64/JSON path/规则匹配/改写/脱敏/悬浮面板 |

Cloudflare Worker 不移植，Android 端继续请求同一个 `https://gxzq.yugenpu.com/api/config`。

## 一、本地构建

需要 JDK 17 + Android SDK（compileSdk 34）。仓库不提交 Gradle wrapper，用系统 gradle 8.7：

```bash
gradle :faker:assembleDebug :monitor:assembleDebug
gradle :faker:assembleRelease :monitor:assembleRelease   # 产出未签名 APK
```

也可以带上版本号（CI 就是这么传的）：

```bash
gradle :faker:assembleDebug :monitor:assembleDebug -PversionName=0.1.0 -PversionCode=42
```

## 二、Actions 流程

与 `.github/workflows/` 下的两个 workflow 一一对应：

- `build-dev.yml`：push 到 `dev`（改动不含 VERSION）时编译两个 debug APK，上传为 Actions 工件，不发布。
- `release.yml`：PR 合入 `main` 后读取 `VERSION`，若标签 `v<VERSION>` 不存在则编译未签名 APK、计算 SHA256、用 PR 描述生成 Release 说明并创建 Release。

发版只需要：改 `VERSION` -> 从 `dev` 提 PR 到 `main` -> 合并。

## 三、接入目标 App

1. **包名**：把 `faker/src/main/res/values/arrays.xml` 与 `monitor/src/main/res/values/arrays.xml` 里的占位包名换成目标 App 的实际包名（阶段 0 待确认），或在 LSPosed 里手动勾选作用域。
2. **安装**：两个 APK 都要装；在 LSPosed 中启用模块并勾选目标 App 的作用域，随后重启目标 App（WebView 类未加载时需要重试安装，见 README 六）。
3. **验证**：先只开 Monitor，确认桥协议与 iOS 一致（阶段 1）；再开 Faker 验证改写（阶段 2-3）。

## 四、悬浮面板

两个模块都通过 hook `Activity.onCreate` 把面板叠加到 `android.R.id.content` 上（免悬浮窗权限），
标题栏可拖动，三指长按 1.2s 显隐（hook `Activity.dispatchTouchEvent` 统计触点）。

## 五、配置格式

`/api/config` 支持三种外壳：``{enabled,rules}``、``{data:{...}}``、``{config:{...}}``。单条规则示例：

```json
{
  "enabled": true,
  "rules": [
    { "id": "price", "action": "queryStockPrice", "path": "$.data.price", "op": "multiply", "value": 1.15 },
    { "id": "freeze", "action": "queryFund*", "path": "$.data.list[*].available", "op": "set", "value": 0 }
  ]
}
```

- `action` 支持 `*` / `?` 通配（也可用 `actionRegex`），留空表示匹配所有 ACTION。
- `op`：`replace` / `set` / `fill` / `multiply` / `add` / `append` / `remove`。
- `path` 是相对 `RESULT` 根的 JSON path，RESULT 是字典或数组两种形态都支持。

## 六、协议要点

JS 侧调用形如：

```javascript
tztCallWebFuctionFromNative(rid, type, "<base64>")
```

第 3 段 Base64 解码后是外层 JSON（含 `ACTION`、`ERRORNO`、`RESULT` 等字段），
`RESULT` 本身又是一段 JSON 文本。改写只替换原 JS 中该段 Base64，其余字符原样保留。
`ACTION` 取自解码后的外层 JSON，不是函数第一个参数。
