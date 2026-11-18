# neko patches for sing-box 1.14.4

These patches turn upstream SagerNet/sing-box v1.14.4 (tag commit
`c005f35300ce76427dc285481ec5695f5b179cd2`) into the 1.14.4-neko-1 tree
KoNeko uses. They are applied in numeric order by
`buildScript/lib/core/get_source.sh` (CI and local libcore builds).

Why patches live here: this directory is under `buildScript/`, so the
libcore cache key in `.github/workflows/release.yml` (`golang_status` =
`find buildScript ...`) changes whenever a patch changes, forcing a
libcore rebuild. A patch placed anywhere else would be silently ignored
by the cache and CI would keep building the old core.

## Patch list

- `01-boxapi.patch` — new package `boxapi` (go_http.go, go_net.go,
  v2ray_server.go, v2ray_stats_service.go): V2Ray stats/API service used
  by libneko.
- `02-nekoutils.patch` — new package `nekoutils` (callback.go, srs.go):
  `Selector_OnProxySelected` hook and headless geo rule getters
  (`GetGeoIPHeadlessRules`, `GetGeoSiteHeadlessRules`).
- `03-box.patch` — box.go: cache file is created only when CacheFile is
  enabled (drop the `|| options.PlatformLogWriter != nil` condition).
- `04-dialer.patch` — common/dialer/default.go: `DoNotSelectInterface`
  global flag; when true, DialContext and ListenPacket skip interface
  selection.
- `05-version.patch` — constant/version.go: version string
  `"1.14.4-neko-1"`.
- `06-selector.patch` — protocol/group/selector.go: call
  `nekoutils.Selector_OnProxySelected` when a proxy is selected.
- `07-vless.patch` — protocol/vless/outbound.go: when multiplex is
  enabled, clear the flow (xtls-rprx-vision is incompatible with mux).
- `08-rule-set-local.patch` — route/rule/rule_set_local.go: handle
  `geoip:` / `geosite:` local rule-set paths via nekoutils headless
  rules instead of loading a file.

## Verification

Fresh clone of pristine v1.14.4 + `git apply` of all 8 patches in order
reproduces the local sing-box tree byte-for-byte
(`diff -r` empty, version `1.14.4-neko-1`). Re-run that check after any
patch change.
