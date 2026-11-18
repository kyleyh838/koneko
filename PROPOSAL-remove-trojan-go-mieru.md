# 待议：移除 trojan-go 与 mieru 两个协议（2026-11-15 用户提出）

## 背景
- trojan-go 插件无处下载：Play 商店下架、F-Droid 查无此包、MatsuriDayo/plugins 仓库无发布、插件文档站无提及。
- mieru 插件停在 2024-01-09 的 v2.2.0，上游已到 v3.39.0（2026-10-23），差一个大版本。
- 用户手上没有真实的 trojan-go / mieru 节点（2026-11-15 测试用的两个是假节点）。

## 移除范围（2026-11-15 全树核查，35 个文件命中）
**删整个文件：**
- fmt/trojan_go/TrojanGoBean.java、TrojanGoFmt.kt；fmt/mieru/MieruBean.java、MieruFmt.kt
- ui/profile/TrojanGoSettingsActivity.kt、MieruSettingsActivity.kt
- res/xml/trojan_go_preferences.xml、mieru_preferences.xml

**改代码：**
- ProxyEntity.kt：TYPE_TROJAN_GO / TYPE_MIERU 常量、（反）序列化分支、needExternal() 分支、displayType()
- fmt/KryoConverters.java：trojanGoDeserialize / mieruDeserialize
- fmt/TypeMap.kt："trojan-go" / "mieru" 两项
- fmt/PluginEntry.kt：TrojanGo / MieruProxy 两项
- ktx/Formats.kt：trojan-go:// 链接分发 + import
- group/RawUpdater.kt：订阅导入里的 parseTrojanGo 调用
- group/GroupUpdater.kt：TrojanGoBean 的 SNI 回填分支
- bg/proto/BoxInstance.kt：两协议的 initPlugin、buildTrojanGoConfig / buildMieruConfig、配置文件落盘与 MIERU_CONFIG_JSON_FILE 环境变量
- bg/Executable.kt：EXECUTABLES 里的 "libtrojan-go.so"
- ui/ConfigurationFragment.kt：添加菜单处理 + 编辑页路由
- res/menu/add_profile_menu.xml：action_new_trojan_go、action_new_mieru
- res/values/strings.xml + values-fa + values-uk：action_trojan_go、action_mieru
- res/values/arrays.xml：trojan_go_networks、trojan_go_security、mieru_protocol 三组
- app/src/main/AndroidManifest.xml：两个 SettingsActivity 声明（168、171 行）
- README.md：协议清单第 44 行 Trojan-Go、第 46 行 Mieru

**数据库（需定方案）：**
- ProxyEntity 表里有 trojanGoBean / mieruBean 两列（schemas 1–7.json 均有记录，历史文件不动）。
- requireBean() 对未知类型直接抛错：旧类型的数据行不处理，读到就崩。须先删掉两个假测试节点，再定：删列升 v8 迁移，还是留死字段只删功能代码。

**不用动：**
- Go 核心侧（libcore / sing-box）零引用，两个协议纯 App 侧外部二进制。
- 无单测引用。
- 历史文件不动：schemas 1–7.json、CORE-UPGRADE-PLAN.md、批次记录。

## 不动的部分
- 插件系统本身（PluginManager、外部二进制运行链）保留：Hysteria1 非 UDP 与 NEKO 类型还要用。

## 待确认
- 用户是否还有真实的 trojan-go / mieru 节点（有则需定数据处理方案）。
- 刚建的两个假测试节点先删掉。
- 彻底移除，还是只从添加菜单隐藏、保留已建节点的读取。
- 用户确认无补充后动手。

## 执行记录（2026-11-15 已执行）
- 用户 15:48 确认「没了，全面清除吧」，按实施计划 `docs/superpowers/plans/2026-11-15-remove-trojan-go-mieru.md` 执行完毕，v1.4.29（versionCode=365）已于 16:14 交付。
- 「待确认」三项均已解决：用户手上无真实的 trojan-go/mieru 节点；全面清除（非仅隐藏菜单）；两个假测试节点由升级迁移自动删除。
- 实施要点与裁决见计划文件末尾 Rulings 1–5（升版提前、表名 proxy_entities、AutoMigration(6,7) 改手动、7.json 从实验树恢复、Migration7To8.kt 排除出零引用断言、Manifest 漏的 scheme 与 6 组数组）。
- 结果：单测 70/70 全绿；迁移校验脚本通过；APK 核验全过（versionCode 365/1.4.29、签名指纹与 release keystore 一致、合并 Manifest 零引用、核心字串 1.14.4-neko-1 在）。
- 升级会删掉两个假测试节点，真实节点不受影响；添加菜单里不再有 trojan-go/mieru。
