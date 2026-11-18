# Remove trojan-go and mieru protocols Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Completely remove the trojan-go (TYPE 7) and mieru (TYPE 21) protocols from KoNeko — code, UI, resources, plugin entries, and database columns — and ship it as v1.4.29.

**Architecture:** Pure deletion plus one manual Room migration (7→8) that deletes stale rows of those two types and drops their two BLOB columns. No replacement functionality. The plugin system itself (PluginManager, external-binary runner) stays for Hysteria non-UDP and NEKO. Go core side has zero references to either protocol and is untouched.

**Tech Stack:** Kotlin/Java (Android), Room, Gradle 8.10.2, JUnit file-parse tests (this codebase's test style).

**Spec:** `~/workspace/tools/nekobox-tmp/PROPOSAL-remove-trojan-go-mieru.md` (verified full inventory: 35 files reference either protocol).

## Global Constraints

- Main tree only: `~/workspace/tools/nekobox-tmp`. The experiment tree `~/workspace/tools/nekobox-exp` is an archive — do not touch it.
- Historical files are never edited: `app/schemas/.../1.json`–`7.json`, `CORE-UPGRADE-PLAN.md`, `.superpowers/sdd/nkb-ui-rewrite/batch-*.md`.
- Every APK delivery increments version: v1.4.29, `VERSION_CODE` 73 → versionCode 365. Run `gradle --stop` before the release build (Helpers.kt reads nb4a.properties once per Daemon).
- Build env: `GRADLE_USER_HOME=/home/hatch/.gradle-nkb3`, `JAVA_HOME=~/workspace/tools/jdk17`, `JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true -Djava.io.tmpdir=/home/hatch/workspace/tmp"`, `nkmr_minify=0`, `--max-workers=1`. Temporarily lower gradle.properties `-Xmx4g` → `-Xmx1200m` for builds, restore after.
- This worktree has no git history and is never committed; verification is tests + builds + APK checks, not commits. (Plan "commit" steps are skipped by design.)
- No changes beyond removing these two protocols. Do not "improve" neighboring code.

## Review Focus

- Stale DB rows of type 7/21: after removal `requireBean()` throws `error("Undefined type …")` on them, so the migration must delete those rows or the profile list crashes on open.
- `DROP COLUMN` needs SQLite 3.35+ (Android 12+). Target device is Android 15, fine; older devices would hit `fallbackToDestructiveMigration()` (full data loss). Accept and note it.
- A leftover reference anywhere breaks compilation (Kotlin/Java) or the release build (Manifest, resources). The no-references test must cover all of `app/src/main`.
- A `trojan-go://` link arriving via subscription import must not crash — the dispatcher branch is deleted, so the import path must fall through to its existing unknown-link handling.
- Hysteria non-UDP and NEKO still need the external-binary path — removing the two protocols must not break `BoxInstance` external handling, `Executable.killAll`, or `PluginEntry` for the remaining entries.

---

### Task 1: Failing tests first

**Files:**
- Modify: `app/src/test/java/io/nekohasekai/sagernet/ui/SingBox114ConfigTest.kt` (DB version test)
- Create: `app/src/test/java/io/nekohasekai/sagernet/ui/ProtocolRemovalTest.kt`
- Create: `~/workspace/tools/nekobox-tmp/.superpowers/sdd/remove-trojan-go-mieru/migration-7-8-check.py` (migration SQL verification script, run by hand, not JUnit)

**Interfaces:**
- Consumes: existing test style (file-parse tests, unit-test workdir is `app/`, paths like `src/main/...` and `../README.md`).
- Produces: `ProtocolRemovalTest` (no-references assertion) and updated DB version test, both consumed by every later task's green check.

- [ ] **Step 1: Write the failing tests in `ProtocolRemovalTest.kt`**
  - `no trojan-go or mieru references remain in app source`: walk `src/main` (java, res, AndroidManifest.xml), assert no file content matches `(?i)trojan.?go|mieru` (case-insensitive; `trojan-go`, `trojan_go`, `TrojanGo`, `mieru` all match).
  - `database is version 8 with a migration from 7`: parse `src/main/java/io/nekohasekai/sagernet/database/SagerDatabase.kt`, assert `version = 8` and a `Migration(7, 8)` (manual) or `AutoMigration(from = 7, to = 8)` is present; assert no `AutoMigration(from = 6, to = 7)`-only state remains (6→7 entry stays as history).
  - Update the existing `database version is 7 with an auto-migration from 6` test in `SingBox114ConfigTest.kt` to version 8 / migration from 7.
- [ ] **Step 2: Run tests to verify they fail**

Run: `GRADLE_USER_HOME=/home/hatch/.gradle-nkb3 gradle :app:testOssDebugUnitTest --max-workers=1 --console=plain` (env per Global Constraints; log to `~/workspace/nkb-remove-test1.log`)
Expected: the 3 new/updated tests FAIL (references still exist; version still 7). All other 65 tests pass.
- [ ] **Step 3: Write the migration SQL verification script**
  - `migration-7-8-check.py`: builds a SQLite DB from `app/schemas/.../7.json` `createSql` for `proxy_entities` (the real table name — `ProxyEntity` is the class name, not the table), inserts one row of each type (7, 21, and a normal type e.g. 2), runs the exact SQL of `MIGRATION_7_8` (DELETE + 2× DROP COLUMN), then asserts: rows of type 7/21 gone, normal row intact, `trojanGoBean`/`mieruBean` columns gone, remaining table definition matches `8.json` `createSql` for `proxy_entities`.
  - This script is written now (it defines the SQL the migration must contain) but can only run GREEN after Task 6 produces `8.json`.
- [ ] **Step 4: Run the script to verify it fails now**

Run: `python3 <script>`
Expected: FAIL (no `8.json` yet / migration SQL not yet defined — the script exits non-zero naming the missing piece).

### Task 2: Remove the model layer

**Files:**
- Modify: `app/src/main/java/io/nekohasekai/sagernet/database/ProxyEntity.kt` (lines 68-69, 95, 100, 180-181, 204-205, 232-233, 332-333, 376-377, 419-420, 424-425, 506-507)
- Modify: `app/src/main/java/io/nekohasekai/sagernet/fmt/KryoConverters.java` (imports + `trojanGoDeserialize` + `mieruDeserialize`)
- Modify: `app/src/main/java/io/nekohasekai/sagernet/fmt/TypeMap.kt` (lines 12-13)
- Modify: `app/src/main/java/io/nekohasekai/sagernet/fmt/PluginEntry.kt` (TrojanGo + MieruProxy enum entries)

**Interfaces:**
- Consumes: `ProtocolRemovalTest` from Task 1 (still red).
- Produces: no `TYPE_TROJAN_GO` / `TYPE_MIERU` / `trojanGoBean` / `mieruBean` identifiers anywhere in `app/src/main/java`.

- [ ] **Step 1: Delete the identifiers**
  - `ProxyEntity.kt`: delete the two bean fields, the two `TYPE_*` constants, the two deserialize branches, the two `displayType()` lines, the two `requireBean()` branches, the two `needExternal()` lines, the two null-reset lines, the two `setBean`-style branches (type assignment + bean assignment), and the two editor-activity mapping lines (506-507).
  - `KryoConverters.java`: delete both imports and both deserialize methods.
  - `TypeMap.kt`: delete the `"trojan-go"` and `"mieru"` entries.
  - `PluginEntry.kt`: delete the `TrojanGo(...)` and `MieruProxy(...)` enum entries (keep `NaiveProxy` and `Hysteria`).
- [ ] **Step 2: Run the no-references test**

Run: same test command as Task 1, filtered to `ProtocolRemovalTest` if the runner supports it, else the full suite.
Expected: still FAIL — Task 3–5 references remain (that is correct at this point, not a regression).

### Task 3: Remove the protocol implementations and runtime paths

**Files:**
- Delete: `app/src/main/java/io/nekohasekai/sagernet/fmt/trojan_go/TrojanGoBean.java`, `app/src/main/java/io/nekohasekai/sagernet/fmt/trojan_go/TrojanGoFmt.kt`, `app/src/main/java/io/nekohasekai/sagernet/fmt/mieru/MieruBean.java`, `app/src/main/java/io/nekohasekai/sagernet/fmt/mieru/MieruFmt.kt`
- Modify: `app/src/main/java/io/nekohasekai/sagernet/bg/proto/BoxInstance.kt` (imports + `is TrojanGoBean` branch with `initPlugin("trojan-go-plugin")` + `buildTrojanGoConfig` + `trojan_go_*.json` file writing; `is MieruBean` branch with `initPlugin("mieru-plugin")` + `buildMieruConfig` + `mieru_*.json` file writing + `MIERU_CONFIG_JSON_FILE` env)
- Modify: `app/src/main/java/io/nekohasekai/sagernet/bg/Executable.kt:13` (remove `"libtrojan-go.so"` from `EXECUTABLES`)
- Modify: `app/src/main/java/io/nekohasekai/sagernet/group/GroupUpdater.kt` (remove the `TrojanGoBean` import and the `is TrojanGoBean` SNI branch)

**Interfaces:**
- Consumes: Task 2 output (model layer gone).
- Produces: no `TrojanGoBean` / `MieruBean` / `buildTrojanGoConfig` / `buildMieruConfig` identifiers; `BoxInstance` external handling still compiles and still serves Hysteria/NEKO.

- [ ] **Step 1: Delete the 4 files and the branches**
  - Delete the 4 implementation files. In `BoxInstance.kt` remove both bean branches in full (plugin init, config build, file writing, env map). In `Executable.kt` remove only the `"libtrojan-go.so"` entry. In `GroupUpdater.kt` remove the import and the SNI branch.
- [ ] **Step 2: Compile the app module**

Run: `gradle :app:compileOssDebugKotlin --max-workers=1 --console=plain`
Expected: BUILD SUCCESSFUL (failures here mean a leftover reference — fix by deleting it, not by stubbing).

### Task 4: Remove the import paths

**Files:**
- Modify: `app/src/main/java/io/nekohasekai/sagernet/ktx/Formats.kt` (remove `parseTrojanGo` import and the `startsWith("trojan-go://")` dispatch branch)
- Modify: `app/src/main/java/io/nekohasekai/sagernet/group/RawUpdater.kt` (remove the `parseTrojanGo` call site)

**Interfaces:**
- Consumes: Task 3 output.
- Produces: a `trojan-go://` link falls through to the existing unknown-link handling instead of crashing; subscription import compiles.

- [ ] **Step 1: Delete the dispatch and call site**
  - `Formats.kt`: delete the import and the whole `else if (startsWith("trojan-go://"))` branch (including its log line).
  - `RawUpdater.kt`: delete the `parseTrojanGo` call site; do not add a replacement branch.
- [ ] **Step 2: Compile**

Run: same compile command as Task 3.
Expected: BUILD SUCCESSFUL.

### Task 5: Remove the UI and resources

**Files:**
- Delete: `app/src/main/java/io/nekohasekai/sagernet/ui/profile/TrojanGoSettingsActivity.kt`, `app/src/main/java/io/nekohasekai/sagernet/ui/profile/MieruSettingsActivity.kt`, `app/src/main/res/xml/trojan_go_preferences.xml`, `app/src/main/res/xml/mieru_preferences.xml`
- Modify: `app/src/main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt` (imports + `action_new_trojan_go` / `action_new_mieru` menu handling + edit-page routing for the two types)
- Modify: `app/src/main/res/menu/add_profile_menu.xml` (delete the two items)
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fa/strings.xml`, `app/src/main/res/values-uk/strings.xml` (delete `action_trojan_go`, `action_mieru`)
- Modify: `app/src/main/res/values/arrays.xml` (delete `trojan_go_networks_entry/value`, `trojan_go_security_entry/value`, `mieru_protocol`)
- Modify: `app/src/main/AndroidManifest.xml` (delete the two SettingsActivity declarations, lines 168 and 171)

**Interfaces:**
- Consumes: Tasks 2–4 output.
- Produces: `ProtocolRemovalTest` no-references assertion passes (this is the task that turns it green).

- [ ] **Step 1: Delete the UI files and references**
  - Delete the 4 files. Remove the menu handling + routing, the menu items, the strings in all 3 locales, the 3 array groups, and the 2 Manifest declarations.
- [ ] **Step 2: Run the no-references test**

Run: full unit-test command (Task 1).
Expected: `ProtocolRemovalTest.no trojan-go or mieru references remain in app source` PASSES. The DB version test still FAILS (Task 6 not done) — expected.

### Task 6: Database migration 7→8

**Files:**
- Modify: `app/src/main/java/io/nekohasekai/sagernet/database/ProxyEntity.kt` (entity fields already removed in Task 2 — this task owns the version + migration wiring)
- Modify: `app/src/main/java/io/nekohasekai/sagernet/database/SagerDatabase.kt` (`version = 7` → `8`; add `AutoMigration(from = 6, to = 7)` stays; register the manual migration)
- Create: `app/src/main/java/io/nekohasekai/sagernet/database/Migration7To8.kt` (or a companion-object entry in `SagerDatabase.kt` — either, one place only)

**Interfaces:**
- Consumes: Task 2 output (entity fields gone).
- Produces: `MIGRATION_7_8: Migration(7, 8)` whose SQL is exactly what `migration-7-8-check.py` executes; `8.json` generated at compile time.

- [ ] **Step 1: Write `MIGRATION_7_8`**
  - `override fun migrate(db: SupportSQLiteDatabase)`: `DELETE FROM proxy_entities WHERE type IN (7, 21)` (backtick `type` if needed), then `ALTER TABLE proxy_entities DROP COLUMN trojanGoBean`, then `ALTER TABLE proxy_entities DROP COLUMN mieruBean`. (Table name corrected 2026-11-15 during execution: the entity class is `ProxyEntity` but the table is `proxy_entities` — verified against `7.json` and the KSP error message.)
  - Bump `version = 8` in `@Database`. Register with `.addMigrations(MIGRATION_7_8)` on the builder in `SagerDatabase` companion (leave the commented-out roomigrant line alone). Keep `fallbackToDestructiveMigration()` as-is.
  - Do NOT add an `AutoMigration(from = 7, to = 8)` — the manual migration owns this step.
- [ ] **Step 2: Run the migration check script**

Run: `python3 <script>` (after a compile generates `8.json`: run `gradle :app:compileOssDebugKotlin` first if `8.json` is absent).
Expected: PASS — stale rows deleted, normal row kept, both columns gone, table definition matches `8.json`.
- [ ] **Step 3: Run the DB version test**

Run: full unit-test command (Task 1).
Expected: DB version test PASSES. Full suite green (68 tests, adjusted count if the rename changed it — report the actual total).

### Task 7: Docs, version bump, release build, delivery

**Files:**
- Modify: `README.md` (delete lines 44 and 46 in the protocol list)
- Modify: `nb4a.properties` (`VERSION_NAME=1.4.29`, `VERSION_CODE=73`, `PRE_VERSION_NAME=pre-1.4.28`)
- Modify: `~/workspace/tools/nekobox-tmp/PROPOSAL-remove-trojan-go-mieru.md` (mark executed, add outcome)
- Modify: `~/MEMORY.md` (record v1.4.29)

**Interfaces:**
- Consumes: Tasks 1–6 output (green suite, verified migration).
- Produces: `~/workspace/KoNeko-1.4.29-arm64-v8a.apk` delivered to the user.

- [ ] **Step 1: Update README and bump the version**
  - Delete the Trojan-Go and Mieru lines from the README protocol list. Set `VERSION_NAME=1.4.29`, `VERSION_CODE=73`, `PRE_VERSION_NAME=pre-1.4.28`.
- [ ] **Step 2: Release build**

Run: `gradle --stop`, then `nkmr_minify=0 gradle app:assembleOssRelease --max-workers=1 --console=plain` (log to `~/workspace/nkb-remove-release-v1429.log`). Temporarily `-Xmx4g` → `-Xmx1200m` in `gradle.properties` if memory is tight; restore after.
Expected: `RELEASE BUILD EXIT: 0`, `KoNeko-1.4.29-arm64-v8a.apk` in `app/build/outputs/apk/oss/release/`.
- [ ] **Step 3: Verify the APK**
  - `aapt2 dump badging`: package `com.koneko.android`, versionCode `365`, versionName `1.4.29`.
  - `apksigner verify --print-certs`: SHA-256 `124d11a298e17d26b812235d068e1c6531d08062716b7a096805426f33bb819e`.
  - `aapt2 dump xmltree --file AndroidManifest.xml`: zero matches for `TrojanGo` / `Mieru`.
  - `unzip -p <apk> lib/arm64-v8a/libgojni.so | strings | grep -o "1.14.4-neko-1"`: present (core untouched).
  - Full-tree grep: zero matches for `(?i)trojan.?go|mieru` in `app/src/main`; remaining matches only in historical files (schemas, `CORE-UPGRADE-PLAN.md`, batch records) and the proposal file.
  - Copy to `~/workspace/KoNeko-1.4.29-arm64-v8a.apk`.
- [ ] **Step 4: Deliver and record**
  - Send the APK to the user as a chat attachment. Tell them: the two fake test nodes will be deleted by the upgrade migration (their real nodes are untouched); trojan-go/mieru no longer appear in Add or anywhere else.
  - Update the proposal file (executed + outcome) and `~/MEMORY.md` (v1.4.29 entry).

## Rulings (执行中裁决，2026-11-15)

### Ruling 1 — Task 6 的数据库升版必须提前到 Task 2 之后做（顺序调整）
- 触发：Task 2 完成后跑单测，KSP 在 `:app:kspOssDebugKotlin` 失败：Room 编译期校验发现实体（已删两列）与 7.json 不一致，要求为 trojanGoBean 的删除声明 `@DeleteColumn` spec。也就是说，**删列之后、升版之前，中间态根本编不过**——原计划「Task 6 最后再升版」的假设是错的。
- 裁决：Task 6 的代码部分（新建 `Migration7To8.kt`、SagerDatabase version 7→8、注册 `.addMigrations(MIGRATION_7_8)`）提前到 Task 2 之后立即执行；Task 6 剩余的验证步骤（跑 migration-7-8-check.py、全量单测绿）仍按原顺序在 Task 5 之后做。Task 3–5 的删除不碰实体，不会再触发 KSP 问题。
- 附加纠正：迁移 SQL 的表名是 `proxy_entities` 不是 `ProxyEntity`（以 7.json 与 KSP 报错为准）；测试与校验脚本里的表名已同步改掉。

### Ruling 2 — ProxyEntity.kt 的移除点比清单多三处
- 触发：Task 2 动手时发现清单漏了 `exportConfig()` 里的两协议分支（`buildTrojanGoConfig`/`buildMieruConfig`）、链接导出 `when` 里的 `is TrojanGoBean -> toUri()`，以及三个 import（`mieru.buildMieruConfig`、`trojan_go.buildTrojanGoConfig`、`trojan_go.toUri`）。
- 裁决：全部删掉（与清单同类改动，无新增行为）。无引用断言测试会兜底漏网的引用。

### Ruling 3 — AutoMigration(6, 7) 改为手动 MIGRATION_6_7
- 触发：Ruling 1 的升版提前做了之后，KSP 仍在同一处失败。查实：Room 对每个 AutoMigration 都是拿 from 版 schema JSON 与**当前实体**做比对（只自动识别 ADD COLUMN，删列/改名必须配 @DeleteColumn/@RenameColumn spec）。当前实体比 6.json 少了 trojanGoBean/mieruBean 两列，6→7 的自动迁移过不了编译期校验——哪怕删列实际发生在 7→8。
- 裁决：删掉 `AutoMigration(from = 6, to = 7)`，改为手动 `MIGRATION_6_7`（同样的三条 `ADD COLUMN ... BLOB`，snellBean/openVPNBean/openConnectBean，均为 nullable，与 7.json 逐项核对过），与 MIGRATION_7_8 一起注册。手动迁移不受编译期校验，只在运行期跑；最终 schema 由 8.json（构建时从实体导出）兜底，migration-7-8-check.py 另做运行期验证。3→4/4→5/5→6 三个自动迁移保留（与当前实体只差 ADD COLUMN，校验能过）。
- 备选方案（未采用）：给 6→7 加 @DeleteColumn spec。语义是错的（删列发生在 7→8 而非 6→7），且运行期 6→7 就会真的删列，7→8 的 DROP COLUMN 需改条件执行（PRAGMA 判存在），两处机制互相咬合，风险更大。

### Ruling 4 — 7.json 被编译覆盖，已从实验树恢复
- 触发：Task 2 后的单测（version 还是 7、实体已删两列）跑 KSP 时，Room 把当前实体导回了 7.json，真正的 v7 schema（带两列、identityHash 37dae95b…）被覆盖。迁移校验脚本随后建出的「v7 库」根本没有那两列，DROP COLUMN 报 no such column。
- 裁决：从实验树 `~/workspace/tools/nekobox-exp/app/schemas/.../7.json`（v1.4.28 的真实导出）拷回主树，diff 核对过只差两列 + identityHash。教训：**删列期间、升版之前，任何一次编译都会毁掉旧版 schema 快照**——以后凡是改实体字段的活，升版必须和删字段同一批提交，不能留中间态去跑构建。
- 附加修正：校验脚本的 `createSql` 要把 `${TABLE_NAME}` 占位符换成真表名（否则建出的表名就是字面 `${TABLE_NAME}`）；最终表结构比对要去掉 `IF NOT EXISTS`（sqlite_master 存的建表语句不带它）。

### Ruling 5 — 无引用断言排除 Migration7To8.kt，Manifest 漏了一处 scheme
- 触发：grep 复核发现三处计划外引用：①Migration7To8.kt 的 SQL 必须写列名（`DROP COLUMN trojanGoBean/mieruBean`）才能删列，零引用断言不可能全绿；②AndroidManifest 里 intent-filter 有 `<data android:scheme="trojan-go" />` 一行，清单漏了；③arrays.xml 实际有 6 组相关数组（trojan_go_networks_entry/value、trojan_go_security_entry/value、trojan_go_methods、mieru_protocol），清单只写了 3 个名字。
- 裁决：①零引用断言排除 Migration7To8.kt 这一个文件（它的 SQL 内容由数据库测试逐字钉住）；其余注释里的触发词改写掉。②Manifest 的 trojan-go scheme 行删掉（mieru 本来就没有 scheme）。③6 组数组全删。④以上都已执行，grep 复核只剩 Migration7To8.kt 的两行 DROP COLUMN。
