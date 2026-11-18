# KoNeko 核心升级实验线：sing-box 1.12.19-neko-1 → 1.14.4

用户 2026-11-14 20:24 授权：搭实验线，把前面说的改动（App 适配、新功能）都一起做了。
主树与正式版绝不动；实验全部在这条线内完成，验证全绿后再由用户决定是否搬回主树。

## 目录
- 实验 App 树：`~/workspace/tools/nekobox-exp/`（2026-11-14 从 nekobox-tmp rsync 复制，不含 build/.gradle，193M）
- 实验 sing-box：`~/workspace/tools/sing-box-1.14.4/`（上游 SagerNet/sing-box v1.14.4 浅克隆，HEAD c005f35）
- 实验 libneko：`~/workspace/tools/libneko-exp/`（从 ~/workspace/tools/libneko 复制，196K）
- 原始件（绝不动）：`~/workspace/tools/nekobox-tmp/`（主树）、`~/workspace/tools/sing-box/`（neko 版 1.12.19，HEAD aed32ee3 "1.12.19-neko-1"）、`~/workspace/tools/libneko/`（MatsuriDayo/libneko，HEAD 1c47a3a 2024-07-02，已是上游最新，无更新版）

## 关键发现：neko 补丁很小（已逐项核实）
上游 v1.12.19 与 neko 版 1.12.19-neko-1 之间只有 11 处差异（`/tmp/sb11219` 为上游 v1.12.19 解压对比）：
1. `box.go`：needCacheFile/needClashAPI 的条件去掉 `options.PlatformLogWriter != nil` 这一项
2. `common/dialer/default.go`：新增全局 `DoNotSelectInterface`，为 true 时 DialContext/ListenPacket 跳过 network strategy 选择
3. `constant/version.go`：版本号字串 "1.12.19-neko-1"
4. `protocol/group/selector.go`：选节点后回调 `nekoutils.Selector_OnProxySelected`
5. `protocol/vless/outbound.go`：multiplex 开启时清掉 Flow
6. `route/rule/rule_set_local.go`：`geoip:`/`geosite:` 开头的本地规则集走 nekoutils 的 headless 规则（App 路由靠这个读 .db）
7. `protocol/tun/inbound.go`：import 改别名 + Close() 里调 `fixGvisorClose()`
8. 新增 `boxapi/`（4 文件：V2Ray 统计服务 + Go http/net 封装，供 App 流量统计用）
9. 新增 `nekoutils/`（callback.go、srs.go）
10. 新增 `protocol/tun/fix_gvisor.go`、`fix_gvisor_stub.go`
重做到 1.14.4 的做法：把上面 1、2、4、5、6、7 项按 1.14.4 的代码现状手工重贴；8、9、10 项整个目录拷过去再修编译错误。注意 1.14 的 box.go 经过重构，1、4、6 项要按新结构落位，不能照搬行号。

## 接线
- `nekobox-exp/libcore/go.mod` 的 replace 现在指 `../../sing-box` 与 `../../libneko`（原始件）。实验线须改成 `../../sing-box-1.14.4` 与 `../../libneko-exp`。
- 同时检查 `libneko-exp/go.mod` 里 sing-box 的 require/replace 是否也要跟进。
- sing-box 1.14 要求 Go 1.25+（已弃 go1.24）；本地 Go 在 `~/workspace/tools/go/bin/go`，版本 1.25.0，够用。
- libcore 构建：`nekobox-exp/libcore/build.sh`，内部用 `gomobile-matsuri bind`（在 `/home/hatch/go/bin/`，已有 gobind/gobind-matsuri/gomobile-matsuri），tags 为 `with_conntrack,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api`，产物拷到 `app/libs/libcore.aar`。构建前确认 `go` 与 gomobile 在 PATH 里，GOPATH 正常。
- **⚠️ gVisor 版本陷阱（2026-11-14 21:0x 实测）**：sing-box 1.14.4 要求 `gvisor v0.0.0-20260727.0-sing-box-mod.1`（日期简写版），但 libcore 的 `go mod tidy` 选了旧的 `v0.0.0-20250325023245-7a9c0f5725fb`——semver 比较时 8 位日期 `20260727` 在数值上小于 14 位时间戳 `20250325023245`，MVS 误以为旧版更新；旧 gVisor 没有 `stack.Route.WritePacketDirect`，sing-tun v0.9.7（2026-10-23）编不过，gomobile 在 armeabi-v7a 报错退出。修法：libcore 里 `go get github.com/sagernet/gvisor@v0.0.0-20260727.0-sing-box-mod.1` 显式钉住（Go 会报 "downgraded"，不用理），再 `go mod tidy`。
- **gomobile 构建前必须先跑带完整 tag 的 Go 构建**：`go build -tags with_conntrack,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api ./...`——无 tag 的 `go build ./...` 编不到 `stack_gvisor_icmp.go` 这类 tag 文件，第一次就因此白跑 15 分钟才在 gomobile 里炸。

## App 适配（nekobox-exp 内做）
- `app/src/main/java/io/nekohasekai/sagernet/fmt/ConfigBuilder.kt:180`：删 `independent_cache = true`（1.14 已删该字段，不删启动时拒掉整个配置）。
- **⚠️ WireGuard 出站已删（1.14 实测）**：`protocol/wireguard/` 只剩 endpoint.go，`option` 里无 WireGuardOutboundOptions，`wireguard.RegisterOutbound` 不存在。App 里现有的 WireGuard 节点（出站 type "wireguard"）在 1.14 核心上建不起来，必须改成 endpoint 方式出配置（wireguard endpoint + 路由走它）。这是 App 适配里最大的一块，ConfigBuilder 与 WireGuardBean 都要动。
- **WireGuard 改 endpoint 已完成（2026-11-14 21:1x，单测 45/45 全绿）**：SingBoxOptions.java 新增 `Endpoint` 基类（type/tag）+ `Endpoint_WireGuardOptions`（address/private_key/peers/mtu 等，删已不存在的 Outbound_WireGuardOptions），`WireGuardPeer` 改 1.14 形状（address/port/public_key/pre_shared_key/allowed_ips/persistent_keepalive_interval/reserved，删旧 server/server_port）；MyOptions 加 `endpoints` 列表；WireGuardFmt.kt 的 builder 改出 endpoint，单 peer、allowed_ips 固定 `0.0.0.0/0` + `::/0`（endpoint peer 无默认值，不写就只走一半流量），reserved 沿用 base64 的 genReserved（[]uint8 的 JSON 就是 base64）；ConfigBuilder 的 buildChain 里 WG 节点进 endpoints 列表、其余出站照旧，tag 不变（proxy/selector/路由照旧按 tag 找 endpoint）；单测 SingBox114ConfigTest 加 2 例钉住。
- DNS 规则的 `ip_cidr`（ConfigBuilder.kt 的 makeDnsRuleObj 与 `moe/matsuri/nb4a/SingBoxOptionsUtil.kt`）：1.14 已废弃、1.16 删除，迁到 evaluate + match_response 写法。注意：路由规则里的 `ip_cidr`/`source_ip_cidr` 是正常规则项，不用动；App 不产生 `ip_version`/`query_type`，所以 1.14 的"混用即拒"不触发。
- App 未用 inline ACME 与 store_rdrc，这两处不用动。
- Kotlin 与核心的接口很小（约 11 个 `Libcore.*` 调用：newSingBoxInstance、initCore、versionBox、newHttpClient、urlTest、stunTest、nekoLogPrintln/Clear、resetAllConnections、unxz、forceGc），只要 libneko 对外还导出同样函数，Kotlin 侧不用改。关于页版本号运行时从核心读，升完自动变 1.14.4。

## 新协议（都要能在 App 里建节点，不只是核心支持）
- 1.13 新增：NaiveProxy；1.14 新增：Snell、OpenVPN、OpenConnect。
- 每个协议按 NekoBox 现有协议的套路加：`fmt/` 下 Bean（含订阅链接解析）、`ui/` 编辑界面、图标、ConfigBuilder 出站生成。参考现有 Hysteria2/TUIC 的 Bean 与编辑器。
- **⚠️ 协议形态已核实（2026-11-14 查 option/ 源码）**：NaiveProxy 与 Snell 是出站（`NaiveOutboundOptions`、`SnellOutboundOptions`），可按普通出站 Bean 加；**OpenVPN 与 OpenConnect 只有 endpoint（`OpenVPNEndpointOptions`、`OpenConnectEndpointOptions`），没有出站类型**，和 WireGuard 一样得走 endpoint 出配置，与 WireGuard 迁移同一套路。Snell 出站字段：version 4/6、psk、userkey、reuse，v4 另有 obfs 选项、v6 另有 V6 选项；NaiveProxy 出站字段：username、password、quic、extra_headers 等。
- **NaiveProxy 已完成（2026-11-14 21:2x，单测 47/47 全绿）**：Bean/解析/编辑界面本来就有（旧插件时代留下的），只缺原生出站——新增 `Outbound_NaiveOptions` + `buildSingBoxOutboundNaiveBean`（proto https/quic → quic 布尔值 + TLS，extra_headers 行解析成 name→values 映射，sni/certificates 进 TLS，sUoT 走通用后处理），ConfigBuilder 加分支，`ProxyEntity.needExternal()` 的 TYPE_NAIVE 改 false（不再起 naive-plugin 外置进程）；单测 SingBox114ConfigTest 加 2 例。注意：`udp_over_tcp: true` 布尔值在 1.14 仍合法（UDPOverTCPOptions 的 UnmarshalJSON 先试布尔值）。
- **Snell 已完成（2026-11-14 21:3x，单测 49/49 全绿）**：全新协议，按 TUIC 模板全套——`SnellBean`（version/psk/userKey/reuse/obfsMode/obfsHost/v6Mode，Kryo v1）+ `SnellFmt`（`snell://psk@host:port?version=4&obfs=http&obfs-host=…&userkey=…&reuse=1&mode=…#名` 解析与生成）+ `Outbound_SnellOptions` + ConfigBuilder 分支 + ProxyEntity TYPE_SNELL=23 全套注册 + KryoConverters + TypeMap + RawUpdater 订阅解析 + SnellSettingsActivity（版本切换 v4/v6 显示不同字段）+ preferences/menu/strings/arrays + Formats.kt 链接路由；单测加 2 例（文件断言 + 真实解析断言）。**教训：`toUri()` 不能在单测里跑**——它调 `urlSafe()`，而 UtilsKt 静态初始化会反射读 `Socket.getFileDescriptor$`（安卓隐藏 API，测试 JDK 上没有），类初始化直接炸；toUri 的往返改到真机 debug 包阶段验证。
- **OpenVPN + OpenConnect 已完成（2026-11-14 21:4x，单测 53/53 全绿）**：两者都是 endpoint（ConfigBuilder 的 endpoint 分支已从 `is Endpoint_WireGuardOptions` 泛化为 `is Endpoint`）。OpenVPN：`OpenVPNBean`（network/mode/username/password/address/cipher/dataCiphers/auth/CA+客户端证书+密钥/peer 指纹/tls_auth·tls_crypt 控制 wrap/static_key/remote_random/route_no_pull/redirect_gateway/mtu，Kryo v1，默认端口 1194，canTCPing=false）+ `openvpn://host:port?network=…&username=…&password=…&cipher=…&auth=…#名` 解析（用户名密码同时支持 userinfo 与 query 参数）+ `Endpoint_OpenVPNOptions`（+ OpenVPNOutboundTLSOptions + OpenVPNControlWrapOptions）；OpenConnect：`OpenConnectBean`（flavor/username/password/authGroup/cookie/token mode·secret·pin/reportedOs/userAgent/localHostname/noUdp/mtu/TLS，默认端口 443，canTCPing=false）+ `openconnect://host[:port]?flavor=…&…#名` 解析 + `Endpoint_OpenConnectOptions`（+ OpenConnectTokenOptions + OpenConnectTLSOptions；server 是单个字串，非 443 端口才拼 `:port`）。两者都按 Snell 全套注册（ProxyEntity TYPE_OPENVPN=24、TYPE_OPENCONNECT=25、KryoConverters、TypeMap、Formats 链接路由、SettingsActivity + preferences/menu/strings/arrays、DataStore 新键一批）；**RawUpdater 未加这两项**——Clash YAML 里没有 openvpn/openconnect 类型，无从映射。单测加 4 例（文件断言 + 真实解析断言；解析器用户名密码双来源——第一轮测试因链接把用户名放 query 而解析器只读 userinfo 失败，已修）。
- **⚠️ 真机首测失败（2026-11-14 22:22，用户回报）**：tcping 正常；URL Test 一闪而过全部超时；点连接报错 `decode config: dns: legacy DNS fakeip options are deprecated in sing-box 1.12.0 and removed in sing-box 1.14.0`。根因：**此前「配置层唯一必改的是删 independent_cache」的结论是错的**——ConfigBuilder 的 DNS 段还发了一整套 1.12 旧格式（只查了 store_rdrc/ip_version/query_type，没查 fakeip 与服务器格式）。1.14 的 DNS 迁移（已按 1.14.4 源码核实）：①顶层 `dns.fakeip` 对象整个删掉，fakeip 改成一台服务器 `{type:"fakeip", tag:"dns-fake", inet4_range:"198.18.0.0/15", inet6_range:"fc00::/18"}`；②每台服务器必须带 `type`（无 type 即 legacy，被拒）：dns-local→`type:"local"`，dns-direct/dns-remote 按地址协议头定 type（裸 IP→udp，udp/tcp/tls/https/quic/h3:// 前缀各归其 type），`address` 改 `server`+`server_port`（https 另拆 path，默认 direct `https://223.5.5.5/dns-query`、remote `https://dns.google/dns-query`）；③`address_resolver` 字段已不存在，改 `domain_resolver`（可直接写字串 `"dns-local"` / `"dns-direct"`，strategy 并进对象里）；④`dns-block` 服务器（`rcode://success`）无对应传输类型，删服务器，指向它的 DNS 规则改 `action:"predefined"`（不填 rcode/answer 即空 NOERROR，与原来等价；reject 默认是 REFUSED，不等价；respond 须前置 evaluate，不能用）；⑤DNS 规则的 `server`/`disable_cache` 仍有效。URL Test 闪退同因：forTest 配置里没有 fakeip，但 dns-block/dns-local/dns-direct 三台旧格式服务器照样在，decode 直接失败；tcping 不走 sing-box 配置所以正常。修法：`SingBoxOptions.java` 的 DNSServerOptions 加 `type`/`server`/`server_port`/`path`/`domain_resolver`（删 address/address_resolver/address_strategy/address_fallback_delay？strategy 并进 domain_resolver 对象，address_fallback_delay 1.14 已无对应字段）+ DNSOptions 删 fakeip 字段 + DNSRule_DefaultOptions 加 `action` 字段；ConfigBuilder 的 DNS 段按上述映射重写，并补单测钉住新格式（假 DNS 开关 on/off、forTest 各一例）。
- **全面核查结果（2026-11-14 22:2x–22:3x，用户要求「全面查一下还有没有漏的」）**：工具：①字段对照脚本 `~/workspace/tools/sing-box-audit/field_diff.py`（SingBoxOptions.java 143 类 vs 1.14.4 option/*.go 264 结构体，解析别名/嵌入/flatten 字段）；②真解码器 `~/workspace/tools/sing-box-audit/sing-box-audit`（Go 小程序，用 sing-box 自身 `include.Context` + `json.UnmarshalExtendedContext[option.Options]` 解码；代表性配置 `representative-current.json` 复现了用户报错原文，证明核查工具真实有效）；③官方迁移文档（1.11/1.12/1.14 节）+ 1.14.4 源码逐项核对。**除 DNS 外只漏了一处**：**Mixed 入站的 `sniff`/`domain_strategy`/`sniff_override_destination`**——1.14 的 `HTTPMixedInboundOptions` 不含 `InboundOptions` 嵌入，App 在 ConfigBuilder:239-241 设置这三项会直接 decode 失败（DNS 修好后下一个报错就是它）；TUN 入站的同名字段仍在（TunInboundOptions 含 InboundOptions 嵌入），不用动；Direct 入站（插件映射用）没设这三项，不用动。修法按官方 1.11 迁移：Mixed 入站删三项，改在 route.rules 最前面加两条规则——`{"inbound":["mixed-in"],"action":"resolve","strategy":…}`（strategy 非空时）与 `{"inbound":["mixed-in"],"action":"sniff"}`（needSniff 时）；`sniff_override_destination` 在规则动作里无等价字段（RouteActionSniff 只有 sniffer/timeout），Mixed 上丢掉（仅影响本地 mixed 代理上 IP 目标 TLS 流量的覆盖行为，TUN 保留完整覆盖行为）。另已核实无恙（不用动）：DNS 规则的 `outbound:["any"]` 字段仍在 1.14.4 运行时生效（route/rule/rule_dns.go:315 仍建 OutboundRule，文档说 1.14 移除未兑现）；TUN 的 inet4/6_address、stack、endpoint_independent_nat 仍在；Direct 入站/出站的 override_address/override_port 仍在（deprecated）；出站 `domain_strategy` hack、udp_over_tcp、multiplex 仍在；cache_file（store_fakeip/store_rdrc）App 从不发；ECH 旧字段 App 从不发；Clash API 字段未变；路由规则 outbound/action/reject/hijack-dns、rule_set（local binary）未变；geosite/geoip 均走 rule_set 引用；https DNS 服务器不用显式 tls（SNI 从 server 主机名派生，path 缺省 /dns-query、端口缺省 443）；DNS 规则未用 ip_cidr/ip_is_private/evaluate/match_response/ip_version，故不受 1.14 地址过滤新限制影响。DNS 策略映射细节：服务器级 strategy 拆两路——顶层 `dns.strategy` = remote 策略（final 服务器），指向 dns-direct 的规则带 `strategy` = direct 策略；两台服务器的 domain_resolver 均用字串形式（"dns-local"/"dns-direct"，解析服务器主机名时默认走顶层策略）；DNSRule_DefaultOptions 需新增 `action` 与 `strategy` 字段；DNSServerOptions 新字段 `type`/`server`/`server_port`/`path`/`domain_resolver`（字串）/`inet4_range`/`inet6_range`/`interface`（dhcp/mdns 用），`detour` 保留（DialerOptions 里仍有）；URL 解析 helper 按协议头分 type（裸 IP/域名→udp；udp/tcp/tls/https/quic/h3/dhcp/mdns 前缀各归其 type；显式端口进 server_port，https/h3 的路径进 path）。
- **DNS + Mixed 修复已完成（2026-11-14 22:3x–22:44，单测 59/59 全绿）**：①`SingBoxOptionsUtil.kt` 新增纯函数 `parseDnsServerAddress`（+ `DnsServerAddress` 数据类：type/server/serverPort/path/iface；裸 host→udp；dhcp://auto→dhcp 无 server、dhcp://eth0→interface；local/fakeip 直通；IPv6 括号端口正确处理）与真实行为单测 8 例；②`SingBoxOptions.java`：DNSServerOptions 改新字段（type/server/server_port/path/domain_resolver/inet4_range/inet6_range/@SerializedName("interface") interface_/detour），删 DNSOptions.fakeip 字段与 DNSFakeIPOptions 类，DNSRule_DefaultOptions 加 action/strategy，Rule_DefaultOptions 加 strategy；③`ConfigBuilder.kt`：DNS 段重写（顶层 dns.strategy=remote 策略；local/fakeip/direct/remote 四台服务器新格式，direct/remote 用 parseDnsServerAddress 解析、domain_resolver 分别为 "dns-local"/"dns-direct"；-1L/loopback/force-direct 三条 dns-direct 规则带 direct 策略；-2L 规则改 action="predefined"；删 dns-block 服务器与 dns.fakeip 对象）、Mixed 入站删三项、route.rules 最前面加 mixed-in 的 resolve/sniff 规则。④验证：代表性配置（正常 + forTest 两版）经真解码器 `sing-box-audit` 均 DECODE OK（forTest 版即 URL Test 走的那条配置）；字段对照脚本重跑只剩已知无害项（DNSServerOptions 的 union 壳字段、从不实例化的服务端入站类、已死的 WireGuardOutboundOptions 旧类）。⑤实验版 debug 包重编已交付（`~/workspace/KoNeko-1.4.27-arm64-v8a-debug.apk`，29,432,850 字节，包名 com.koneko.android.debug、versionCode=355、versionName=1.4.27、核心字串 1.14.4-neko-1 已核对），等用户真机复测：连接、URL Test、订阅更新、逐节点对比。
- **⚠️ 核查方法教训（2026-11-14 22:49，用户复测又报错）**：只验 JSON 解码不够——旧字段能解码通过、却在建服务时才被拒。用户 22:49 回报点连接仍报错（截图原文）：「失败:: create service: initialize inbound[0]: legacy tun address fields are deprecated in sing-box 1.10.0 and removed in sing-box 1.12.0」。根因：TUN 入站的 `inet4_address`/`inet6_address`（1.10 合并进 `address`，1.12 删）与 `domain_strategy`/`sniff`/`sniff_override_destination`（InboundOptions，1.13 删）——前者只在 `protocol/tun/inbound.go:68` 建服务时才检查（TUN 不走 ListenOptions，所以解码期不拦），后者同文件 :76；`endpoint_independent_nat` 在 1.14 全树无读取（死字段）。核查工具已升级：`sing-box-audit` 除解码外再跑 `box.New`（建服务，含全部入站/出站/DNS/路由初始化检查；geoip:/geosite: 规则集用 nekoutils 回调桩返回空；须带与手机 libcore 相同的构建 tag `with_conntrack,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api` 编，否则 clash_api 配置会误报 "not included in this build"）。升级后的工具在旧代表性配置上**原样复现了用户的 TUN 报错**，修后两版配置均 DECODE OK + SERVICE CREATE OK。另已全树扫完所有 "removed in sing-box" 初始化期检查：除 TUN 外无一命中 App 的发出项（direct 出站的 proxy_protocol 与 override、ECH 旧字段、clash_api 内 cache_file，App 一概不发；direct 入站的 override_address/override_port 仍合法，链式映射继续用）。
- **TUN 修复（2026-11-14 22:5x，单测 61/61 全绿）**：`SingBoxOptions.java` 的 TunInboundOptions 与 Inbound_TunOptions 两类：`inet4/inet6_address`、`inet4/inet6_route_address`、`endpoint_independent_nat` 五字段删，加 `public List<String> address;`；`ConfigBuilder.kt`：TUN 入站改 `address` 单列表（按 ipv6Mode 合并原两条 VLAN 地址，原前缀 /28、/126 不变），删三项入站选项，`needSniffOverride` 局部变量已删（无处再用）；路由规则的 resolve/sniff 改为共用入站列表 `sniffInbounds`（mixed-in + 有 VPN 时加 tun-in 在首位），`sniff_override_destination` 在 TUN 上同样丢掉（规则动作里无等价字段，仅影响 IP 目标 TLS 流量的覆盖行为，与 Mixed 同口径）。代表性配置已同步新形状并重验。修复版 debug 包 2026-11-14 22:58 已交付（29,429,602 字节，同包名/版号/核心字串已核对）。**URL Test 仍待用户在新包上重测**：forTest 配置在核查里解码与建服务均通过，配置层面已无可查的失败点；若新包上 URL Test 仍失败，需用户报当时具体表现（超时/报错文）再查拨号期原因。
- **DNS detour 修复（2026-11-14 23:0x–23:12，单测 62/62 全绿）**：用户 23:04 发录屏：URL Test 跑到 13/29 后失败，报错「失败:: start dns-transport: start dns/https[dns-direct]: detour to an empty direct outbound makes no sense」。根因：1.14 的 detour 检查（common/dialer/detour.go:72）——DNS 服务器的 `detour: "direct"` 指向的 direct 出站是空的（plain `{"type":"direct"}`，DialerOptions 全默认，`IsEmpty()` 为真）时，建服务不拦、启动时才报错。1.12 时同位置有 `legacyDNSDialer` 豁免（dns/transport_dialer.go 传 `Legacy: options.Legacy`），1.14 已删该豁免（改成 `DisableEmptyDirectCheck`，且 `json:"-"`、配置里设不了，DNS 传输也不传它）。修法：direct 出站加 `_hack_config_map["udp_fragment"] = true`（仅 direct 出站，bypass 不动；DNS 服务器的 detour 保留）。选这项的理由：行为零变化——`common/dialer/default.go:191` 里 UDPFragment 为空时取 UDPFragmentDefault（direct 出站构造里已置 true），显式写 true 与默认值完全等效，只把 isEmpty 翻成 false；domain_strategy 备选方案被否掉（它会改 direct 流量的解析策略，是真实行为变化）。核查工具再升级：`sing-box-audit` 在解码+建服务之后再跑 `box.Start()` + `Close()`（URL Test 的失败正是启动期才暴露的；代表性配置新增无 TUN 版 `representative-fixed-notun.json`，因沙箱开不了 TUN 设备）。验证结果：forTest 配置解码+建服务+启动+关闭全过；正常配置（去 TUN 版）同样全过，mixed 入站已监听、clash API 已起；带 TUN 的正常配置在沙箱里启动失败于 `open tun: TUNSETIFF: operation not permitted`——纯环境限制（沙箱无权建 TUN 设备），手机上由系统 VPN 服务给 TUN fd、与 1.12 正式版同一条路，不属配置问题。修复版 debug 包 2026-11-14 23:12 已交付（29,481,248 字节，同包名/版号/核心字串已核对），等用户复测连接、URL Test、订阅更新与逐节点对比。
- **订阅更新偶尔报错（2026-11-15 05:56 用户报，原因已查明）**：两张图是同一条取订阅流程的两面。图一 RST：订阅更新先走本地 mixed 入口的 SOCKS5（只在 VPN 连着时才通），不通就兜底直连订阅服务器；直连是往 Cloudflare IPv6 地址 443 端口去的，读数据时被对方 reset——网络侧偶发干扰，跟升核心无关（取订阅根本不走 sing-box 核心，用的是 libcore 自己的 HTTP 客户端，`libcore/http.go` 与主树逐字相同）。图二 NPE：`RawUpdater.kt` 里 `execute()` 返回后直接调 `response.contentString`，没判空；Go 侧 `doH3Direct()` 在两路直连都挂掉、10 秒超时、且没记下任何错误时，会返回（nil, nil）——无回应也无错误，于是 Java 侧拿到 null 就空指针。这是预先就有的小 bug，正式版 1.4.27 同样有，只是平时很少触发。**修复已完成（2026-11-15 06:0x–06:13，用户 06:00 批准）**：Go 侧 `libcore/http.go` 的 `doH3Direct()` 在 `ctx.Done()` 分支里 `finalErr == nil` 时改返回 `ctx.Err()`（超时错误），回归测试 `libcore/http_test.go`（本地静默监听器让 ECH 握手挂满 10 秒，复现前返回 (nil, nil) 失败、修复后通过）；Kotlin 侧 `RawUpdater.kt` 在 `execute()` 后加 `?: error("empty response")` 判空（单测文件解析断言钉住）。单测 63/63 全绿（Go 测试 1 例 + App 62 例）。libcore.aar 已重编（59,704,891 字节；重编教训：`build.sh` 要 `ANDROID_HOME` 指到 workspace 的 SDK 且 PATH 里要有 jdk17/bin 否则 javac 找不到——第一次失败正是缺这两样，旧 aar 在 app/libs 里未被覆盖故无损）；第四版修复包 2026-11-15 06:13 已交付（44,728,597 字节，比前三版大：zip 里多了约 15MB 的对齐填充，apksigner v1+v2 验证通过、zipalign 检查通过、zip 完整性 OK，安装不受影响）。用户追问对齐填充是什么；2026-11-15 06:48 干净重编验证：填充是增量打包留下的（干净编后包回落到 29,317,854 字节、填充只剩约 190KB），已把干净版换上去交付。用户 2026-11-15 06:53 在第四版包上逐协议复测：Hysteria2、VLESS、VMess、TUIC、AnyTLS、Shadowsocks、HTTPS、SOCKS5、Trojan 共 9 种全部 URL Test 与连接通过；未测的还有 WireGuard（改 endpoint，重点）、NaiveProxy（改原生出站，重点）、Snell/OpenVPN/OpenConnect（新增）、Hysteria1、Trojan-Go、ShadowTLS、Mieru、SSH，用户手上暂无这些协议的节点。RST 本身代码修不了，连着 VPN 再更新订阅可避开直连那条路。
- 不确定某个协议在 1.14 的 option 字段时，以 `sing-box-1.14.4/option/` 与 `transport/` 源码为准，别凭印象编字段。

## 构建环境纪律（照主树那套，实验线单独一份）
- 实验线专用 `GRADLE_USER_HOME=/home/hatch/.gradle-nkb-exp`（不要和主树的 `~/.gradle-nkb3` 混用，Daemon 会打架）。
- `JAVA_HOME=/home/hatch/workspace/tools/jdk17`，`JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true -Djava.io.tmpdir=/home/hatch/workspace/tmp"`，TMPDIR/GOTMPDIR=`~/workspace/tmp`，PATH 含 jdk17/bin 与 gradle-8.10.2/bin（`~/workspace/tools/gradle-8.10.2/bin`）。
- MITM 代理 `~/workspace/tools/mitm/mitm-proxy.py`（127.0.0.1:3134）：构建前查 `ps -eo args | grep -q '[m]itm-proxy.py'`，不在就重拉；`systemProp` 代理须写进实验 GRADLE_USER_HOME 的 gradle.properties 才进 Daemon；出口会丢弃无 ALPN 的 JDK TLS 握手，Gradle 不走代理必超时。
- gradle.properties 的 `-Xmx` 构建时临时改 1200m（内存紧），构建完恢复 4g。
- **版号陷阱**：buildSrc 的 requireMetadata() 把 nb4a.properties 缓存在 Daemon 内存里，改版号后不杀 Daemon 就构建会打出旧版本号；发版构建前必须先 `gradle --stop`。实验线先不改版号（保持 1.4.27），只编 debug 包验证。
- debug 包自带 `applicationIdSuffix = "debug"`，与正式版包名不同，可与手机上的正式版 KoNeko 并存安装。
- `/tmp` 只有 512MB tmpfs，大文件放 workspace 并用 `curl -C -` 断点续传；Go/Gradle 构建把 TMPDIR 指到 `~/workspace/tmp`。
- 构建日志写 `~/workspace/nkb-exp-<步骤>.log`。
- TDD：改动先写失败单测再实施（沿用主树 KoNekoRenameTest 的风格，解析真实资源/源码文件断言）；全量单测全绿是出包前置条件（主树现为 41 个）。

## 验证（实验版 debug 包）
- 手机上的正式版 KoNeko 绝不卸载、不覆盖。
- 从正式版导出备份（`koneko_backup_YYYYMMDD_HHMMSS.json` 格式），导入实验版 debug 包，节点与订阅完全一致。
- 逐个节点验证：连通、测速、订阅更新、TUN、kill-switch（C2 修复的行为）；同一个节点正式版与实验版各测一遍对比。
- 全绿后才向用户汇报，**搬回主树、打正式版须用户另外拍板**，本任务不擅自搬。

## 已知不做的事
- 不动主树 `nekobox-tmp/`、不动 `~/workspace/tools/sing-box/` 与 `~/workspace/tools/libneko/` 原始件。
- 不推源码、不动 GitHub 仓库。
- 订阅 UA 保持 "NekoBox/Android/"（用户决定，单测钉住）。


## 第五轮：未测协议的核查链接准备（2026-11-15 07:0x，进行中）
- 用户 06:53 复测 9 种协议全过，问还有什么要测。核对 ProxyEntity 全部类型后，未测的有：WireGuard（改 endpoint，重点）、NaiveProxy（改原生出站，重点）、Snell/OpenVPN/OpenConnect（新增）、Hysteria1、Trojan-Go、ShadowTLS、Mieru、SSH。
- 逐个查解析器/构建器后确认：
  - 可链接导入：WireGuard（无 URI 解析器，须用标准 [Interface]/[Peer] 配置文本导入）、Naive（naive+https://user:pass@host:port）、Snell（snell://psk@host:port?version=4&obfs=http&obfs-host=）、OpenVPN（openvpn://user:pass@host:port）、OpenConnect（openconnect://user:pass@host:port）、Hysteria1（hysteria://host:port?peer=&auth=&insecure=1）。
  - 无链接解析器、只能手动建节点：ShadowTLS、Mieru、SSH。
  - Trojan-Go 与 Mieru 的 needExternal()=true，走外部二进制、不走 sing-box 核心，升核心与它们无关，不用测。
  - WireGuard 测试密钥（随机生成、仅用于配置验证，无真实服务器）：私钥 +6IVlZTQU7pZRsCtoyc9LJtsG0cNVVVlH7tDJL1fSyE=，公钥 MKQubZgmc2hGaI57CZPfnKDWsQfLZ84lRxs+zOA7cM4=；假服务器统一用 192.0.2.1（TEST-NET-1，不可达）。
- **核查工具先行验证 6 种配置（代表性配置 forTest 骨架 + 该协议出站/endpoint）时抓到真问题：**
  - WG endpoint、Snell、Hysteria1：解码+建服务+启动+关闭全过。
  - Naive：建服务失败 "naive outbound is not included in this build, rebuild with -tags with_naive_outbound"。
  - OpenVPN：解码失败 "unknown endpoint type: openvpn"。
  - OpenConnect：建服务失败 "OpenConnect is not included in this build, rebuild with -tags with_openconnect,with_gvisor"。
  - 根因：sing-box 1.14 里这三个功能都在构建开关后面（protocol/naive/outbound.go 与 include/naive_outbound.go 带 //go:build with_naive_outbound；include/openvpn.go 带 with_openvpn；include/openconnect.go 带 with_openconnect），而 libcore/build.sh 的 tags 只有原来 6 个（with_conntrack,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api），主树原版 build.sh 同样只有这 6 个（新协议以前不存在，所以从未缺过）。**手机上的旧实验包同样缺这三个功能**，用户若直接拿链接去测会报同样的错。
  - 修法：build.sh 的 tags 补 with_naive_outbound,with_openvpn,with_openconnect（已改）。
- **Cronet 链接陷阱（沙箱核查工具专属，真机不受影响）：** with_naive_outbound 会把 protocol/naive/outbound.go 编进来，它 import github.com/sagernet/cronet-go（NaiveProxy 在 1.14 里跑在 Cronet 之上）。Linux 下 go build 链接失败：ld 报 libcronet.a "skipping incompatible"（-l: 方式），但直接把 .a 当文件传给 gcc 又能链过；查明 .a 里的对象全是标准 x86-64 ELF64，问题出在这些对象本身（多份同名 .text 段、sh_info 异常，像是部分链接/LTO 产物），bfd 的 -l: 兼容检查在首个对象是这类对象时拒绝整个包，放一个普通对象在最前就能过。**这是 GNU ld + 该预编译包的问题，不是代码问题；**安卓编包用 NDK 的 lld + android_arm64 版 libcronet.a，不走这条路。核查工具的 Linux 链接问题暂未绕（可改 cgo LDFLAGS 走直接路径，但没必要——Naive/OpenVPN/OpenConnect 以真机为准，其余三种核查工具已验证通过）。
- libcore.aar 已用新 tags 重编（2026-11-15 07:xx，build 日志 ~/workspace/nkb-exp-libcore-rebuild3.log），随后重编 debug 包、验证后连同测试链接一起发给用户。


## 第五轮续：补 tags 后 libcore 重编遇阻（2026-11-15 07:1x）
- build.sh tags 已补 with_naive_outbound,with_openvpn,with_openconnect，但 gomobile 编 arm64-v8a 的 libgojni.so 时链接失败（LIBCORE BUILD EXIT: 1，旧 .aar 未被覆盖，仍是 06:11 的 59,704,891 字节版）：
  - 报错全部来自 android_arm64 的 libcronet.a（cronet-go v0.0.0-20260912104006）：`unknown relocation (315) against symbol typeinfo for std::bad_alloc` 等。315 号是 AArch64 的 PAC 指针认证重定位（R_AARCH64_AUTH_ABS64），该预编译库用了新版 Clang 的分支保护；NDK 25（clang-14 / LLD 14）不认识 315 号重定位，链不过。
  - 根因链：NaiveProxy 在 sing-box 1.14 里跑在 Cronet 之上（protocol/naive/outbound.go 直接 import cronet-go），with_naive_outbound 一开就把 57MB 的 libcronet.a 拉进链接；OpenVPN/OpenConnect 不拉 cronet。
  - 排查过程（沙箱 Linux 侧同款问题）：Linux 下 go build 同样链不过，ld 报 libcronet.a "skipping incompatible"（-l: 方式），但直接把 .a 当文件传给 gcc 又能过；查明 .a 内对象全是标准 x86-64 ELF64，问题是这些对象本身（多份同名 .text 段、sh_info 异常，像部分链接/LTO 产物），bfd 的 -l: 兼容检查在首个对象是这类对象时拒绝整个包，首个放普通对象就能过。这是 GNU ld + 该预编译包的怪癖，不是代码问题。
- 对策（2026-11-15 07:1x 进行中）：
  1. 先隔离验证：build.sh 临时去掉 with_naive_outbound，只留 with_openvpn,with_openconnect，看 NDK 25 下能否链过（rebuild4，日志 ~/workspace/nkb-exp-libcore-rebuild4.log）。
  2. Naive 需要新工具链：正在下载 NDK r27c（android-ndk-r27c-linux.zip，dl.google.com 直连，断点续传，后台进行中），其 LLD 18 认识 315 号重定位；到手后用新 NDK 重编 3-tag 版 libcore。
  3. 注意：build.sh 目前是临时 2-tag 状态，3-tag 重编前须把 with_naive_outbound 加回去。


## 第五轮续 2：OpenVPN 构建器两处真 bug（2026-11-15 07:2x，核查工具逐项挖出）
- **类型字串错**：App 的 buildSingBoxEndpointOpenVPNBean 发 `type = "openvpn"`，但 1.14 的 endpoint 注册名是 `constant.TypeOpenVPNClient = "openvpn-client"`（服务端是 "openvpn-server"），"openvpn" 本身不是已知类型，解码直接失败。修法：改成 `"openvpn-client"`（OpenVPNFmt.kt）。字段本身没问题（server/server_port/network/mode/username/password/address/cipher/data_ciphers/auth/mtu/remote_random/route_no_pull/redirect_gateway/static_key/key_direction/tls 都在 option.OpenVPNClientEndpointOptions 里）。
- **tls 对象缺失**：构建器原来只在用户填了 caCert/clientCert/clientKey/peerFingerprint/controlWrap 任一项时才发 tls；tls 模式下 sing-box 要求 tls 非空（protocol/openvpn/client.go:180 "missing `tls` options"），无证书的常用节点会直接建服务失败。修法：mode != "static_key" 时一律发 tls。
- **空 tls 对象会被解码成 nil**：实测 `"tls": {}` 解码后 TLS 指针仍是 nil（sing-box 的 JSON 层把空对象当未设置），所以光发空对象不够。修法：tls 对象里没别的字段时填 `remote_certificate_tls = "server"`——这是核心在字段缺省时自己用的默认值（client.go 里空值即补 "server"），行为不变；为此给 SingBoxOptions 的 OpenVPNOutboundTLSOptions 类补了 `remote_certificate_tls` 字段（与 Go 结构体对齐）。注意不能拿 server_name 当填充物：它会被用作证书名校验，填服务器地址会改验证行为。
- **tls 模式还要求 CA 或指纹**：tls 非空后下一关是 "tls mode: either certificate-authority or peer-fingerprint must be configured"——TLS 模式必须配 CA 证书或对端指纹其一（这是 sing-box 的硬性要求，OpenVPN 1.14 客户端没有系统 CA 兜底）。测试链接用一个随机生成的假指纹（64 位 hex：e50e529b08f1e940a8349e33f4ca7652c681880e25e23171475f17fc11249c44）通过；真节点由用户在编辑页填 CA 或指纹。
- **解析器补参**：parseOpenVPN 原来不读指纹（注释说证书密钥太大、不进链接——指纹是单个 hex 串，不在此列）。已补 `peer-fingerprint` 查询参数的解析与 toUri 往返，测试链接因此可以自包含。
- 核查结果（2-tag 核查工具，openvpn+openconnect）：WG/Snell/Hysteria1/OpenConnect 全过；OpenVPN 在上述三处修完后，`"tls": {"remote_certificate_tls": "server", "peer_fingerprint": [...]}` 的配置解码+建服务+启动+关闭全过（test-openvpn.json）。
- 单测：SingBox114ConfigTest 新增 2 例（OpenVPN 链接解析含指纹；endpoint 类型 + tls 常发 + 填充物 + 解析器 + 字段），共 65 例，运行中（日志 ~/workspace/nkb-exp-debug-build7-test.log）。
- NDK r27c 已下好并解压到 android-sdk/ndk/android-ndk-r27c（clang/LLD 18.0.3，认识 315 号 PAC 重定位）；build.sh 已恢复 3-tag，待单测通过后用 NDK 27 重编 libcore（ANDROID_NDK_HOME 指向 r27c）。


## 第五轮续 3：NDK 27 接线（2026-11-15 07:3x）
- NDK r27c（27.2.12479018）已下好，解压在 android-sdk/ndk/27.2.12479018（clang/LLD 18.0.3）。
- 接线教训：`buildScript/init/env_ndk.sh` 把 NDK 路径**写死**为 `$ANDROID_HOME/ndk/25.0.8775105`，只在该路径不存在时才看 ANDROID_NDK_HOME——所以光设环境变量没用；把 25 的目录改名移开又会让脚本直接报 "NDK not found"（重编 7 次失败正是这个原因）。修法：实验线的 env_ndk.sh 改成先试 27.2.12479018，再回落 25.0.8775105（主树的 env_ndk.sh 未动，仍是 25）。注意：25 的目录名已恢复原样，别动它。
- 3-tag libcore 重编（NDK 27）进行中，日志 ~/workspace/nkb-exp-libcore-rebuild8.log。若成功，随后重编 debug 包、跑单测（65 例）、验证后连同测试链接一起发用户。
- 截至目前的核查结论（核查工具，2-tag 版）：WG endpoint、Snell、Hysteria1、OpenConnect 全过；OpenVPN 在修完类型名 + tls 常发 + 指纹填充后全过；Naive 因沙箱 GNU ld 链不过 Cronet 无法在核查工具里验证，以真机为准。


## 第五轮续 4：NDK 27 也链不过——根因是 LLD 版本（2026-11-15 07:40）
- NDK 27（LLD 18）同样报 unknown relocation (315)。查证：LLD 对 AArch64 AUTH 重定位的支持是 2024-04-04 合入的补丁（PR #72714，commit cca9115），属于 LLVM 19——NDK 27 是 LLVM 18，没有；**NDK 28（LLVM 19）起才支持**。结论：NaiveProxy 需要 NDK 28+。
- NDK r28c（722MB）已开始下载（~/workspace/tools/android-ndk-r28c-linux.zip），装好后解压为 android-sdk/ndk/28.2.13676358，env_ndk.sh 再加一档优先 28。
- 备选（未用）：cronet-go 只有 3 个带 tag 的老版本（Chromium 140/143 时代），当前用的是 2026-09 的伪版本；降级可能撞 API，不如换 NDK 干净。
- 当前交付策略（用户已等 50 分钟）：先交付 2-tag 版 debug 包（Naive 除外其余 5 个新/改协议全可用，单测 65/65），Naive 等 NDK 28 编好再补包 + 链接。干净重编进行中（build9，增量包 46MB 里有约 15MB 对齐填充，干净编后应回落到约 31MB）。


## 第五轮续 5：NDK 28 编通 3-tag 核心（2026-11-15 07:57）
- NDK r28c（28.2.13676358，LLVM 19）已装好，env_ndk.sh 优先 28→27→25（实验线；主树仍是 25）。
- 3-tag libcore 重编成功（EXIT 0，日志 ~/workspace/nkb-exp-libcore-rebuild9.log）：产物 85,539,591 字节（之前 2-tag 是 66MB，多了 Cronet），4 个 ABI 齐全，arm64 的 .so 里有 cronet 符号（*cronet.URLRequestStatusListener 等），NaiveProxy 已编进核心。注意：途中服务重启过一次，把第一次重编 9 拦腰打断，重跑才成——长构建被打断后直接重跑即可（gomobile 构建是幂等的，.aar 只有成功才被覆盖）。
- 干净重编 debug 包进行中（build10），编好核验后把 Naive 链接发给用户，6 个半程验证就齐了。
- 2026-11-15 07:44 已交付的包是 2-tag 版（30.9MB，含订阅修复 + OpenVPN 修复 + 5 个测试链接）；本轮的 3-tag 包与它只差核心里的 Naive 开关，App 代码相同。


## 第五轮续 6：3-tag 包交付，6 个链接齐了（2026-11-15 08:01）
- 3-tag debug 包干净重编完成（build10，35,952,020 字节，填充约 190KB，签名验证通过，核心字串 1.14.4-neko-1），已发用户，附 Naive 链接 `naive+https://testuser:testpass@192.0.2.1:443#Test-Naive`。
- 等用户复测：①6 个半程验证结果；②订阅更新（第四版修复重点）；③从正式版导出备份导入实验版的逐节点对比（自始未回报）；④Snell 的 toUri() 往返（导入后分享/导出该节点看链接是否正常）。
- 实验线收尾待办（全绿后）：gradle.properties 的 -Xmx1200m 改回 -Xmx4g；本计划文件已补记到第五轮续 6；搬回主树、打正式版仍须用户另外拍板。


## 第五轮续 7：真根因——libcore 手建注册表漏了 4 个协议（2026-11-15 09:53，用户录屏纠正）
- 用户录屏证实：测的就是实验版 debug 包，关于页显示 sing-box 1.14.4-neko-1 且三个 tag 全在。助手此前"测错 App"的判断是错的，已当面认错。
- 真根因：libcore 不用 sing-box 的 include.Context()，它在 box_include.go 里手建注册表（neko 分叉的设计），而升 1.14 时只改了接口、没把新协议加进手建表：
  - nekoboxAndroidOutboundRegistry() 缺 snell、naive（9 种已过的协议全在表里）；
  - nekoboxAndroidEndpointRegistry() 只注册了 wireguard，缺 openvpn、openconnect。
  - 构建开关只决定代码编不编进二进制，注册表才决定解码时认不认这个类型——所以关于页 tag 全在，解码照样报 unknown type。核查工具用的 include.Context()（上游全量注册表）所以全过，没测出这层。
- 修复（box_include.go + 6 个新文件，照搬上游 include/* 的 real/stub 分法）：
  - snell 无 tag，直接 import + 注册；
  - naive/openvpn/openconnect 各一对 tag-gated 文件（with_* 用真注册，!with_* 用 stub：解码认类型、创建时报"not included in this build"明错，与上游行为一致）；
  - 注册表里加了注释：新协议只加 tag 不够，必须在这里注册。
- 回归测试：libcore/registry_test.go（TestNekoboxAndroidRegistriesKnowNewProtocols）——用 NewSingBoxInstance 同款注册表解码 4 个测试配置。修前跑出手机上一模一样的 4 条 unknown type（RED 已实证）；修后全绿。注意：该测试在沙箱跑时不能带 with_naive_outbound（会拉 Cronet，GNU ld 的 -l: 怪癖链不过；走 stub 路径一样验解码），但 9-tag 全量 go build 已验证通过。
- 3-tag libcore 重编成功（rebuild10，90,560,556 字节，EXIT 0）；debug 包干净重编进行中（build11），编好核验后发用户重测 4 个。
- 教训：以后凡是"核心认不出类型"的报错，先查 libcore 的手建注册表，再查 tag；核查工具的通过≠手机通过，因为两者注册表来源不同。


## 第五轮续 8：Naive 报 missing domain resolver（2026-11-15 10:20，用户复测注册表修复版）
- 用户复测：其余 5 个全过（连接正常、URL Test 超时即算过），Naive 报「失败:: create service: initialize outbound[0]: missing domain resolver for domain server address」。
- 根因（common/dialer/dialer.go:73/129，NewDNSQueryOptions）：
  - Naive 出站把 RemoteIsDomain 与 NewDialer 都硬编码为 true（protocol/naive/outbound.go:99-104），不管服务器是域名还是 IP；OpenVPN/OpenConnect 也是 NewDialer=true，但 RemoteIsDomain 按服务器算（IP 不触发，域名才触发）。
  - 拨号器要解析域名服务器时，先看出站自带的 domain_resolver，再看路由的 default_domain_resolver；都没有时，只有 DNS 传输少于 2 个才兜底用默认传输——App 配置里常年 2 个以上 DNS 服务器，所以 newDialer=true 时直接报错。1.12 时没有这道检查。
  - 注意：这不是注册表问题（注册表修好了，Naive 已能解码、走到建服务这一步）；也不是 DNS 服务器配置问题。
- 修法（App 侧即可，核心不用动）：三个 NewDialer 的出站/端点各加一行 `_hack_config_map["domain_resolver"] = "dns-direct"`（NaiveFmt / OpenVPNFmt / OpenConnectFmt 的构建器里，沿用 App 已有的 domain_strategy、udp_fragment 同款 hack map 机制；Go 侧 DomainResolveOptions 的 UnmarshalJSON 接受纯字串 "dns-direct"，已核实 option/outbound.go:141）。dns-direct 是生成配置里常在的服务器；IP 服务器根本不走解析分支（resolveDialer 对 IP 直通），域名服务器则经直连 DNS 解析——对代理服务器地址来说语义正确。
- 未选的方案：路由级 default_domain_resolver——会改掉所有出站域名服务器的解析路径（从「走 DNS 规则」变成「强制走 dns-direct」），影响已通过的 9 种协议的真实节点，风险面太大；只给 Naive 补是最小改动，OpenVPN/OpenConnect 顺手同补（域名服务器才生效，IP 服务器无影响）。
- 验证（核查工具，2-tag 版）：
  - 新配置 test-openvpn-domain.json（OpenVPN 端点服务器改成域名 vpn.example.com，不带 resolver）：复现一模一样的报错「SERVICE CREATE FAILED: initialize endpoint[0]: missing domain resolver for domain server address」——RED 已实证（Naive 与 OpenVPN 走同一条 dialer 代码路径）。
  - test-openvpn-domain-fixed.json（加 "domain_resolver": "dns-direct"）：解码 + 建服务 + 启动 + 关闭全过（日志里可见解析走了 223.5.5.5:443 的 dns-direct，CLOSE OK）。注意：沙箱里 Naive 本身链不过（GNU ld 的 -l: 怪癖），所以用 OpenVPN 代测机制；Naive 的修复走同一行代码。
  - App 单测新增 1 例（NewDialer outbounds name a domain resolver），先 RED 后 GREEN；全量单测 66/66 全绿（build12-test）。
- 新 debug 包干净重编进行中（build13，App 侧改动而已、核心仍是 rebuild10 的 .aar），编好核验后发用户重测 Naive。预期：不再报 domain resolver，连接/URL Test 报超时即算过。


## 第五轮续 9：编辑页闪退——三个新 Activity 漏写 Manifest（2026-11-15 10:36，用户发崩溃日志）
- 用户复测：6 个协议全报超时（半程验证全部通过）；新问题：Snell/OpenVPN/OpenConnect 三个协议的节点，无论新建还是点卡片编辑，都会弹回桌面并生成崩溃日志。
- 崩溃日志根因（NB4A_Crash_8385659376960491792_0_8hvi.log，10:07:59）：
  `android.content.ActivityNotFoundException: Unable to find explicit activity class {com.koneko.android.debug/io.nekohasekai.sagernet.ui.profile.SnellSettingsActivity}; have you declared this activity in your AndroidManifest.xml?`
  调用栈：ConfigurationFragment.onMenuItemClick:558 → startActivity。三个新协议的 SettingsActivity 类文件都在，但 Manifest 里一行都没加；其余 15 个协议的编辑页全在 Manifest 里（同文件 153-195 行）。新建与编辑走同一启动路径，所以都崩。
- 修法：AndroidManifest.xml 在 TuicSettingsActivity 后补三行（同款 `android:configChanges="uiMode"`）。全量交叉核对：ui/ 下全部 *Activity.kt 与 Manifest 逐一对照，缺的只有这三个，无其他遗漏。
- 单测新增 1 例（new protocol settings activities are declared in the manifest），先 RED 后 GREEN；全量单测 67/67 全绿（build14-test）。
- 新 debug 包干净重编进行中（build15，App 侧改动而已、核心仍是 rebuild10 的 .aar），编好后用 aapt2 核对合并后的 Manifest 里三行都在、签名通过，再发用户验证新建与编辑。
- 教训：新协议全套清单里要加一条——Activity 类建好后必须在 Manifest 里声明；单测现在钉住了这一项，以后加协议也不会再漏。
