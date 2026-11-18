# KoNeko CI: tag-triggered release + sing-box 1.14.4 via patches (Option B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the GitHub Actions release workflow build KoNeko from a tag push, producing an APK with sing-box 1.14.4-neko (patches applied in CI) and publishing it to the GitHub Release; remove the preview workflow.

**Architecture:** Delete `preview.yml`. `release.yml` triggers on push of tags `v*`; the libcore job clones public `SagerNet/sing-box` at v1.14.4 and applies neko patch files stored under `buildScript/lib/core/patches/`; the publish job uploads the APKs to the Release for the pushed tag via `ghr`. `get_source.sh` is updated to the new sing-box source; libneko is untouched.

**Tech Stack:** GitHub Actions, bash, git, Go (setup-go `^1.25`), Gradle, `ghr` v0.13.0.

**Spec:** User decisions 2026-11-18: (1) delete preview workflow; (2) release workflow auto-triggers on tag push and uploads to Release after completion; (3) sing-box sourcing via Option B (clone official SagerNet/sing-box v1.14.4 + apply the 11 neko patches stored as patch files in the repo); (4) patch files live where a change triggers a libcore rebuild.

## Global Constraints

- sing-box base is upstream v1.14.4, tag commit `c005f35300ce76427dc285481ec5695f5b179cd2`.
- Patch files live under `buildScript/lib/core/patches/` so the libcore cache key (`find buildScript` → `golang_status`) changes when a patch changes, forcing a libcore rebuild.
- libneko pin is unchanged: `1c47a3af71990a7b2192e03292b4d246c308ef0b` from `MatsuriDayo/libneko`.
- Release trigger is push of tags matching `v*` only.
- Publish targets the pushed tag (`github.ref_name`), not a workflow_dispatch input.
- The koneko repo needs a `LOCAL_PROPERTIES` secret for APK signing in CI; that is user-side repo setup, not code — flag it in the final report, do not fabricate it.
- This change does not bump the app version by itself (no VERSION_NAME/VERSION_CODE change).
- Go version in CI is pinned to `1.27.1` (same as the VM's local Go) in `release.yml`; `preview.yml` is deleted so only `release.yml` needs the pin.
- New test workflow (user 2026-11-18): a separate `.github/workflows/test.yml` that runs unit tests on every push and does NOT build libcore or the APK. It restores `libcore.aar` from the same actions/cache the release workflow uses (read-only restore, no build step) because at least one unit test (`Fixes1440Test.kt`) imports libcore classes and needs the .aar on the compile classpath.

## Review Focus

- Patches must apply cleanly to pristine v1.14.4 and reproduce the local 1.14.4-neko tree for every patched file; a patch that silently fails to apply (or applies with fuzz/offset) would ship a wrong core.
- A patch-file change that does not invalidate the libcore cache would silently reuse a stale `libcore.aar`; patches must live under `buildScript/` (covered by `golang_status`).
- The tag trigger must not fire on branch pushes or non-version tags.
- Publish must create/update the Release for the exact pushed tag, not a stale or manual tag.
- If the `LOCAL_PROPERTIES` signing secret is missing in the repo, the Gradle build fails at signing; this is a setup step, not a code bug.

---

### Task 1: Generate neko patch files

**Files:**
- Create: `buildScript/lib/core/patches/*.patch` (one file per patched area, numbered, e.g. `01-*.patch`)
- Read-only inputs: `/data/workspace/koneko/sing-box/` (local 1.14.4-neko tree, NOT a git repo), pristine upstream v1.14.4 (clone to a scratch dir, do not commit it)

**Interfaces:**
- Consumes: local sing-box tree (1.14.4 + 11 neko patches, version string `1.14.4-neko-1` in `constant/version.go`); upstream v1.14.4 tag commit `c005f35300ce76427dc285481ec5695f5b179cd2`.
- Produces: patch files under `buildScript/lib/core/patches/`; the list of patched files (for Task 2 and Task 4).

- [ ] **Step 1: Clone pristine v1.14.4 to a scratch dir**

Run: `git clone --no-checkout https://github.com/SagerNet/sing-box.git /tmp/sing-box-pristine && cd /tmp/sing-box-pristine && git checkout c005f35300ce76427dc285481ec5695f5b179cd2`
Expected: checkout succeeds; `constant/version.go` contains `1.14.4` (no `-neko` suffix).

- [ ] **Step 2: Diff local tree vs pristine and write patch files**

Run: `diff -ruN /tmp/sing-box-pristine /data/workspace/koneko/sing-box` (excluding `.git`, build artifacts, and any files that are not part of the neko patches), then split the diff into one `.patch` file per patched area under `buildScript/lib/core/patches/`.
Expected: the diff lists exactly the 11 neko patch areas (plus `constant/version.go` version string); no unrelated files.

- [ ] **Step 3: Verify patches reproduce the local tree**

Run: fresh clone of pristine v1.14.4 to a second scratch dir, `git apply` (or `patch -p1`) every file in `buildScript/lib/core/patches/` in numeric order, then `diff -r` the result against `/data/workspace/koneko/sing-box`.
Expected: zero differences for all patched files (ignoring `.git` and build artifacts).

- [ ] **Step 4: Record the patch list**

Write the list of patch files and what each one changes into the plan's Task 1 note (or a `README` in `buildScript/lib/core/patches/`), so Task 2 and future core upgrades know the set.

### Task 2: Update get_source.sh + get_source_env.sh

**Files:**
- Modify: `buildScript/lib/core/get_source.sh`
- Modify: `buildScript/lib/core/get_source_env.sh`
- Test: simulate `get_source.sh` on the VM (scratch parent dir) and check the result

**Interfaces:**
- Consumes: patch files from Task 1; upstream v1.14.4 commit `c005f35300ce76427dc285481ec5695f5b179cd2`; libneko pin `1c47a3af71990a7b2192e03292b4d246c308ef0b`.
- Produces: `get_source.sh` that clones `SagerNet/sing-box` at v1.14.4 and applies all patches; unchanged libneko sourcing.

- [ ] **Step 1: Update get_source_env.sh**

Set `COMMIT_SING_BOX="c005f35300ce76427dc285481ec5695f5b179cd2"` (was `aed32ee3066cdbc7d471e3e0415c5134088962df`, i.e. 1.12.19-neko-1). Leave `COMMIT_LIBNEKO` unchanged. Add a variable for the sing-box repo URL if the script needs one (e.g. `SING_BOX_REPO="https://github.com/SagerNet/sing-box.git"`).

- [ ] **Step 2: Update get_source.sh sing-box block**

Change the clone URL from `https://github.com/MatsuriDayo/sing-box.git` to the SagerNet URL, keep the `if [ ! -d "sing-box" ]` guard and `git checkout "$COMMIT_SING_BOX"`, then apply every patch in `buildScript/lib/core/patches/` in numeric order (e.g. `for p in buildScript/lib/core/patches/*.patch; do git -C sing-box apply "$p"; done` — use an absolute path to the patches dir, since the script `pushd ..` changes directory). Patches must apply with `set -e` semantics: any failure aborts the build. Do not change the libneko block.

- [ ] **Step 3: Simulate get_source.sh on the VM**

Run: in a scratch dir, copy the repo tree (or run the script from a clean checkout of the repo with an empty parent dir) and execute `bash buildScript/lib/core/get_source.sh`; then verify `sing-box/constant/version.go` reads `1.14.4-neko-1` and the patched files match the local tree (`diff -r` against `/data/workspace/koneko/sing-box`).
Expected: script exits 0; version string is `1.14.4-neko-1`; patched files identical to local tree.

### Task 3: Delete preview.yml; rewrite release.yml

**Files:**
- Delete: `.github/workflows/preview.yml`
- Modify: `.github/workflows/release.yml`
- Test: YAML parse + trigger/publish reference checks

**Interfaces:**
- Consumes: current `release.yml` (workflow_dispatch with `tag`/`publish`/`play` inputs, `ghr` publish, `actions/cache` libcore cache, `actions/upload-artifact@v4` APKs).
- Produces: `release.yml` triggered by tag push, publishing APKs to the Release for the pushed tag; `preview.yml` gone.

- [ ] **Step 1: Delete preview.yml**

Run: `rm .github/workflows/preview.yml`.
Expected: file gone; no other file references it.

- [ ] **Step 2: Change the trigger to tag push**

Replace the `on: workflow_dispatch: inputs: ...` block with:
```yaml
on:
  push:
    tags:
      - 'v*'
```
Remove the `inputs` block entirely.

- [ ] **Step 3: Fix the publish job to use the pushed tag**

Remove the `if: github.event.inputs.publish != 'y'` condition (tag builds always publish). In the Release step, replace `"${{ github.event.inputs.tag }}"` (both the `-n` name and the tag argument to `ghr`) with `"${{ github.ref_name }}"`. Keep the `ghr -delete -t "${{ github.token }}"` invocation and the `apks/` collection unchanged.

- [ ] **Step 4: Fix the play job condition**

Remove the `if: github.event.inputs.play != 'y'` condition so the Play bundle builds on tag push (preserving the original default-on behavior). Leave the rest of the play job unchanged. Note: it uses `actions/upload-artifact@v3` while the rest use v4; leave that as-is (out of scope).

- [ ] **Step 4: Pin the Go version to 1.27.1**

In `release.yml`, change `go-version: ^1.25` to `go-version: '1.27.1'` (same as the VM's local Go, verified `go1.27.1`). `preview.yml` is deleted so only `release.yml` needs this change. sing-box's `go.mod` requires go 1.25.5, so 1.27.1 satisfies it.

- [ ] **Step 5: Validate the YAML and the references**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/release.yml'))"` and `grep -n "github.event.inputs" .github/workflows/release.yml` (expect no matches) and `grep -n "ref_name" .github/workflows/release.yml` (expect matches in the publish step).
Expected: YAML parses; no `github.event.inputs` references remain; `github.ref_name` is used for the tag; `go-version` is `'1.27.1'`.

### Task 4: New test workflow (unit tests on every push, no build)

**Files:**
- Create: `.github/workflows/test.yml`
- Test: YAML parse + verify the test task runs without libcore build on the VM

**Interfaces:**
- Consumes: the repo's unit tests (`:app:testOssDebugUnitTest`, 164 tests); the libcore cache key from `release.yml` (same `golang_status`/`libcore_status` computation, read-only restore).
- Produces: `test.yml` that runs unit tests on every push, builds nothing.

- [ ] **Step 1: Create test.yml**

Trigger: `on: [push]` (every push, any branch). Jobs: one `test` job on `ubuntu-latest`. Steps: checkout@v4, setup JDK 17 (same as release.yml), setup Android SDK (same `android-actions/setup-android@v3` with `packages: platform-tools`), restore `app/libs/libcore.aar` from the actions/cache using the same cache key computation as `release.yml` (read-only; no `setup-go`, no libcore build, no Gradle assemble). Then run the unit tests: `./gradlew :app:testOssDebugUnitTest` (via the repo's `./run` wrapper if it is the established way to run Gradle, else direct `gradlew`). No APK artifact upload.

- [ ] **Step 2: Verify the test task runs without a libcore build**

On the VM, in a clean checkout of the repo with `app/libs/libcore.aar` present (restored, not built) and no `sing-box`/`libneko` source dirs, run the same Gradle test command and confirm it passes (164 tests). This proves the workflow needs only the cached .aar, not a libcore build.
Expected: tests pass; no libcore build step was run.

- [ ] **Step 3: Validate the YAML**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/test.yml'))"` and confirm `on` includes `push` and no `assemble`/`lib core` build step exists in the file.
Expected: YAML parses; trigger is push; no build step.

### Task 5: Verify end-to-end (local simulation)

**Files:**
- Test: scratch dirs on the VM only (no repo changes)

**Interfaces:**
- Consumes: Tasks 1–3 outputs.
- Produces: a verification record (what was checked, what passed).

- [ ] **Step 1: Verify patch application reproduces the local tree**

Run: fresh pristine v1.14.4 clone → apply all patches in order → `diff -r` vs `/data/workspace/koneko/sing-box`.
Expected: zero differences on patched files.

- [ ] **Step 2: Verify the cache key covers the patches**

Run: compute `golang_status` exactly as the workflow does (`find buildScript libcore/*.sh | xargs cat | sha1sum`), touch/modify a patch file, recompute, and confirm the hash changes.
Expected: hash changes when a patch file changes.

- [ ] **Step 3: Verify get_source.sh end-to-end from a clean parent dir**

Run: in an empty scratch parent dir, place a copy of the repo tree, run `bash buildScript/lib/core/get_source.sh`, confirm exit 0, `sing-box/constant/version.go` is `1.14.4-neko-1`, and libneko checked out at `1c47a3a`.
Expected: all confirmed.

- [ ] **Step 4: Verify release.yml**

Run the YAML parse + grep checks from Task 3 Step 5; confirm `on.push.tags` is `['v*']` and no `workflow_dispatch` remains.
Expected: all confirmed.

### Task 6: Sync to sandbox and report

**Files:**
- Sync: VM tree → sandbox `~/workspace/tools/nekobox-tmp` (tarball, per sync discipline: pull, verify sha256, back up sandbox keystore/local.properties, extract, verify version/keystore fingerprint/local.properties paths).

**Interfaces:**
- Consumes: Tasks 1–4 outputs on the VM.
- Produces: sandbox at the same state as the VM; a report to the user.

- [ ] **Step 1: Sync VM → sandbox**

Follow the established sync steps; verify `nb4a.properties` version, `release.keystore` fingerprint `12:4D:11…81:9E` unchanged, `local.properties` still sandbox paths, and the new/changed files present (patches, updated `get_source.sh`/`get_source_env.sh`, updated `release.yml`, `preview.yml` absent).

- [ ] **Step 2: Report to the user**

Report: what changed (preview deleted; release.yml tag-triggered + publishes to Release for the pushed tag; sing-box now via Option B patches; libneko unchanged), what was verified locally, and the remaining user-side setup: (a) the `LOCAL_PROPERTIES` signing secret must exist in the `kyleyh838/koneko` repo settings or the CI Gradle build fails at signing; (b) the real CI test is pushing the source and pushing a `v*` tag — that has not been done (source push is still pending user decision).

---

## Self-Review

**1. Spec coverage:** Delete preview → Task 3 Step 1. Tag trigger → Task 3 Step 2. Upload to Release → Task 3 Step 3. Option B patches → Tasks 1–2. Patches trigger rebuild → Task 1 placement + Task 5 Step 2. Go pin 1.27.1 → Task 3 Step 4. Test workflow on push, no build → Task 4. All covered.

**2. Step scan:** Each step names one action and its checkable result. No step carries more than one decision.

**3. Type consistency:** Commit SHAs, paths (`buildScript/lib/core/patches/`, `.github/workflows/release.yml`, `.github/workflows/test.yml`), the tag variable (`github.ref_name`), and the Go version (`1.27.1`) are used identically across tasks.

**4. Review Focus:** Each line has a task test: patch correctness → Task 1 Step 3 / Task 5 Step 1; cache coverage → Task 5 Step 2; trigger scope → Task 3 Step 2 + Task 5 Step 4; publish tag → Task 3 Step 3; signing secret → Task 6 Step 2 (report, not code); test workflow needing libcore.aar → Task 4 Step 2 (cache restore, no build).

**5. Proportion:** Plan is longer than the spec because the spec is four short user decisions and the plan must pin exact commits, paths, and verification commands the implementer cannot guess. Code bodies are not transcribed; only signatures, values, and checks are given.
