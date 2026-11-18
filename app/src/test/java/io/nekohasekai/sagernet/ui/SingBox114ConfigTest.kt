package io.nekohasekai.sagernet.ui

import io.nekohasekai.sagernet.fmt.openconnect.parseOpenConnect
import io.nekohasekai.sagernet.fmt.openvpn.parseOpenVPN
import io.nekohasekai.sagernet.fmt.snell.parseSnell
import moe.matsuri.nb4a.parseDnsServerAddress
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the sing-box 1.14 core upgrade.
 *
 * sing-box 1.14 removed the `independent_cache` DNS option, the legacy DNS
 * server formats (`address` / `address_resolver` / server-level `strategy`),
 * the top-level `dns.fakeip` object, and the `rcode://` pseudo-servers. A
 * config that still emits any of them is rejected at startup: the first
 * device test failed with exactly the fakeip error, and the mixed inbound
 * also lost its `sniff` / `domain_strategy` fields in 1.14 (the mixed
 * inbound struct no longer embeds them; the TUN inbound still does).
 *
 * These tests read the real source files, so they fail while any of it
 * still mentions the removed fields. The new DNS server address parser
 * is covered by real behavior tests below it.
 */
class SingBox114ConfigTest {

    private fun readSource(relativePath: String): String {
        val file = File(relativePath)
        assertTrue("source file missing: $relativePath", file.exists())
        return file.readText()
    }

    @Test
    fun `ConfigBuilder does not set independent_cache`() {
        val text = readSource("src/main/java/io/nekohasekai/sagernet/fmt/ConfigBuilder.kt")
        assertFalse("ConfigBuilder still sets independent_cache", text.contains("independent_cache"))
    }

    @Test
    fun `SingBoxOptions has no independent_cache field`() {
        val text = readSource("src/main/java/moe/matsuri/nb4a/SingBoxOptions.java")
        assertFalse("SingBoxOptions still declares independent_cache", text.contains("independent_cache"))
    }

    @Test
    fun `WireGuard is built as an endpoint, not an outbound`() {
        // sing-box 1.14 removed the wireguard outbound entirely; WireGuard is
        // now an endpoint (option.WireGuardEndpointOptions). The node must be
        // emitted into the top-level "endpoints" list with the proxy tag, so
        // routes and the selector keep resolving it by tag.
        val fmt = readSource("src/main/java/io/nekohasekai/sagernet/fmt/wireguard/WireGuardFmt.kt")
        assertTrue("WireGuardFmt has no endpoint builder", fmt.contains("buildSingBoxEndpointWireguardBean"))
        assertFalse("WireGuardFmt still builds an outbound", fmt.contains("buildSingBoxOutboundWireguardBean"))
        assertFalse("WireGuardFmt still references the removed outbound options", fmt.contains("Outbound_WireGuardOptions"))
        val builder = readSource("src/main/java/io/nekohasekai/sagernet/fmt/ConfigBuilder.kt")
        assertTrue("ConfigBuilder does not call the endpoint builder", builder.contains("buildSingBoxEndpointWireguardBean"))
        assertFalse("ConfigBuilder still calls the outbound builder", builder.contains("buildSingBoxOutboundWireguardBean"))
        val options = readSource("src/main/java/moe/matsuri/nb4a/SingBoxOptions.java")
        assertTrue("SingBoxOptions has no Endpoint_WireGuardOptions", options.contains("Endpoint_WireGuardOptions"))
        assertFalse("SingBoxOptions still declares the removed outbound options", options.contains("Outbound_WireGuardOptions"))
        assertTrue("MyOptions has no endpoints list", options.contains("public List<SingBoxOption> endpoints;"))
    }

    @Test
    fun `WireGuard endpoint peer covers all traffic`() {
        // An endpoint peer has no default allowed_ips: it must list both
        // 0.0.0.0/0 and ::/0, or the node only routes part of the traffic.
        val fmt = readSource("src/main/java/io/nekohasekai/sagernet/fmt/wireguard/WireGuardFmt.kt")
        assertTrue("endpoint peer misses 0.0.0.0/0", fmt.contains("0.0.0.0/0"))
        assertTrue("endpoint peer misses ::/0", fmt.contains("::/0"))
    }

    @Test
    fun `NaiveProxy has a native sing-box outbound`() {
        // sing-box 1.14 ships NaiveProxy in the core, so the node no longer
        // needs the external naive-plugin binary: the config builder must
        // emit a native "naive" outbound, and needExternal must stop
        // routing NaiveBean to the plugin.
        val fmt = readSource("src/main/java/io/nekohasekai/sagernet/fmt/naive/NaiveFmt.kt")
        assertTrue("NaiveFmt has no outbound builder", fmt.contains("buildSingBoxOutboundNaiveBean"))
        assertTrue("Naive builder does not use Outbound_NaiveOptions", fmt.contains("Outbound_NaiveOptions"))
        val builder = readSource("src/main/java/io/nekohasekai/sagernet/fmt/ConfigBuilder.kt")
        assertTrue("ConfigBuilder does not call the naive builder", builder.contains("buildSingBoxOutboundNaiveBean"))
        val options = readSource("src/main/java/moe/matsuri/nb4a/SingBoxOptions.java")
        assertTrue("SingBoxOptions has no Outbound_NaiveOptions", options.contains("Outbound_NaiveOptions"))
        val entity = readSource("src/main/java/io/nekohasekai/sagernet/database/ProxyEntity.kt")
        assertFalse("NaiveBean is still routed to the external plugin", entity.contains("TYPE_NAIVE -> true"))
    }

    @Test
    fun `NaiveProxy outbound maps proto to quic`() {
        // The bean's proto is https or quic; the 1.14 outbound takes a
        // quic boolean instead.
        val fmt = readSource("src/main/java/io/nekohasekai/sagernet/fmt/naive/NaiveFmt.kt")
        assertTrue("naive builder does not map proto to quic", fmt.contains("bean.proto == \"quic\""))
        assertTrue("naive builder does not emit extra_headers", fmt.contains("extra_headers"))
    }

    @Test
    fun `Snell has a native sing-box outbound`() {
        // sing-box 1.14 ships Snell in the core. It is a new protocol for
        // this app, so it needs the full set: bean + parser, outbound
        // builder, entity registration, subscription scheme, and the
        // add-node menu entry.
        val fmt = readSource("src/main/java/io/nekohasekai/sagernet/fmt/snell/SnellFmt.kt")
        assertTrue("SnellFmt has no parser", fmt.contains("fun parseSnell"))
        assertTrue("SnellFmt has no outbound builder", fmt.contains("buildSingBoxOutboundSnellBean"))
        assertTrue("Snell builder does not use Outbound_SnellOptions", fmt.contains("Outbound_SnellOptions"))
        val builder = readSource("src/main/java/io/nekohasekai/sagernet/fmt/ConfigBuilder.kt")
        assertTrue("ConfigBuilder does not call the snell builder", builder.contains("buildSingBoxOutboundSnellBean"))
        val options = readSource("src/main/java/moe/matsuri/nb4a/SingBoxOptions.java")
        assertTrue("SingBoxOptions has no Outbound_SnellOptions", options.contains("Outbound_SnellOptions"))
        val entity = readSource("src/main/java/io/nekohasekai/sagernet/database/ProxyEntity.kt")
        assertTrue("ProxyEntity has no TYPE_SNELL", entity.contains("TYPE_SNELL"))
        val typeMap = readSource("src/main/java/io/nekohasekai/sagernet/fmt/TypeMap.kt")
        assertTrue("TypeMap has no snell scheme", typeMap.contains("\"snell\""))
        val menu = readSource("src/main/res/menu/add_profile_menu.xml")
        assertTrue("add-node menu has no snell entry", menu.contains("action_new_snell"))
        val strings = readSource("src/main/res/values/strings.xml")
        assertTrue("strings have no action_snell", strings.contains("action_snell"))
    }

    @Test
    fun `Snell link parses`() {
        // NOTE: toUri() cannot run in unit tests: it calls urlSafe(), and
        // UtilsKt's static init reflectively reads Socket.getFileDescriptor$,
        // a hidden Android API that does not exist on the test JDK. The
        // round-trip is verified on device during the debug-package check.
        val bean = parseSnell("snell://psk123@example.com:1234?version=4&obfs=http&obfs-host=cdn.example.com#MySnell")
        assertEquals("example.com", bean.serverAddress)
        assertEquals(1234, bean.serverPort)
        assertEquals(4, bean.version)
        assertEquals("psk123", bean.psk)
        assertEquals("http", bean.obfsMode)
        assertEquals("cdn.example.com", bean.obfsHost)
        assertEquals("MySnell", bean.name)
        val v6 = parseSnell("snell://secret@10.0.0.1:2345?version=6&userkey=uk1&mode=unshaped&reuse=1#V6")
        assertEquals(6, v6.version)
        assertEquals("uk1", v6.userKey)
        assertEquals("unshaped", v6.v6Mode)
        assertEquals(true, v6.reuse)
    }

    @Test
    fun `OpenVPN is built as an endpoint`() {
        // sing-box 1.14 only has an OpenVPN client endpoint, no outbound.
        // It goes into the top-level "endpoints" list, like WireGuard.
        val fmt = readSource("src/main/java/io/nekohasekai/sagernet/fmt/openvpn/OpenVPNFmt.kt")
        assertTrue("OpenVPNFmt has no parser", fmt.contains("fun parseOpenVPN"))
        assertTrue("OpenVPNFmt has no endpoint builder", fmt.contains("buildSingBoxEndpointOpenVPNBean"))
        assertTrue("OpenVPN builder does not use Endpoint_OpenVPNOptions", fmt.contains("Endpoint_OpenVPNOptions"))
        val builder = readSource("src/main/java/io/nekohasekai/sagernet/fmt/ConfigBuilder.kt")
        assertTrue("ConfigBuilder does not call the openvpn builder", builder.contains("buildSingBoxEndpointOpenVPNBean"))
        val options = readSource("src/main/java/moe/matsuri/nb4a/SingBoxOptions.java")
        assertTrue("SingBoxOptions has no Endpoint_OpenVPNOptions", options.contains("Endpoint_OpenVPNOptions"))
        val entity = readSource("src/main/java/io/nekohasekai/sagernet/database/ProxyEntity.kt")
        assertTrue("ProxyEntity has no TYPE_OPENVPN", entity.contains("TYPE_OPENVPN"))
        val typeMap = readSource("src/main/java/io/nekohasekai/sagernet/fmt/TypeMap.kt")
        assertTrue("TypeMap has no openvpn scheme", typeMap.contains("\"openvpn\""))
        val menu = readSource("src/main/res/menu/add_profile_menu.xml")
        assertTrue("add-node menu has no openvpn entry", menu.contains("action_new_openvpn"))
        val strings = readSource("src/main/res/values/strings.xml")
        assertTrue("strings have no action_openvpn", strings.contains("action_openvpn"))
    }

    @Test
    fun `OpenVPN link parses`() {
        val bean = parseOpenVPN("openvpn://vpn.example.com:1194?network=udp&username=u&password=p&cipher=AES-256-GCM&auth=SHA256#MyVPN")
        assertEquals("vpn.example.com", bean.serverAddress)
        assertEquals(1194, bean.serverPort)
        assertEquals("udp", bean.network)
        assertEquals("u", bean.username)
        assertEquals("p", bean.password)
        assertEquals("AES-256-GCM", bean.cipher)
        assertEquals("SHA256", bean.auth)
        assertEquals("MyVPN", bean.name)
    }

    @Test
    fun `OpenConnect is built as an endpoint`() {
        // sing-box 1.14 only has an OpenConnect endpoint, no outbound.
        val fmt = readSource("src/main/java/io/nekohasekai/sagernet/fmt/openconnect/OpenConnectFmt.kt")
        assertTrue("OpenConnectFmt has no parser", fmt.contains("fun parseOpenConnect"))
        assertTrue("OpenConnectFmt has no endpoint builder", fmt.contains("buildSingBoxEndpointOpenConnectBean"))
        assertTrue("OpenConnect builder does not use Endpoint_OpenConnectOptions", fmt.contains("Endpoint_OpenConnectOptions"))
        val builder = readSource("src/main/java/io/nekohasekai/sagernet/fmt/ConfigBuilder.kt")
        assertTrue("ConfigBuilder does not call the openconnect builder", builder.contains("buildSingBoxEndpointOpenConnectBean"))
        val options = readSource("src/main/java/moe/matsuri/nb4a/SingBoxOptions.java")
        assertTrue("SingBoxOptions has no Endpoint_OpenConnectOptions", options.contains("Endpoint_OpenConnectOptions"))
        val entity = readSource("src/main/java/io/nekohasekai/sagernet/database/ProxyEntity.kt")
        assertTrue("ProxyEntity has no TYPE_OPENCONNECT", entity.contains("TYPE_OPENCONNECT"))
        val typeMap = readSource("src/main/java/io/nekohasekai/sagernet/fmt/TypeMap.kt")
        assertTrue("TypeMap has no openconnect scheme", typeMap.contains("\"openconnect\""))
        val menu = readSource("src/main/res/menu/add_profile_menu.xml")
        assertTrue("add-node menu has no openconnect entry", menu.contains("action_new_openconnect"))
        val strings = readSource("src/main/res/values/strings.xml")
        assertTrue("strings have no action_openconnect", strings.contains("action_openconnect"))
    }

    @Test
    fun `DNS servers use the 1-14 format`() {
        // Legacy DNS server formats (no `type`, `address` instead of
        // `server`, `address_resolver` instead of `domain_resolver`,
        // server-level `strategy`) were removed in 1.14.0.
        val options = readSource("src/main/java/moe/matsuri/nb4a/SingBoxOptions.java")
        val serverClass = options.substringAfter("public static class DNSServerOptions")
            .substringBefore("public static class DNSClientOptions")
        assertTrue("DNSServerOptions has no type field", serverClass.contains("public String type;"))
        assertTrue("DNSServerOptions has no server field", serverClass.contains("public String server;"))
        assertTrue("DNSServerOptions has no server_port field", serverClass.contains("public Integer server_port;"))
        assertTrue("DNSServerOptions has no domain_resolver field", serverClass.contains("domain_resolver"))
        assertTrue("DNSServerOptions has no inet4_range field", serverClass.contains("public String inet4_range;"))
        assertTrue("DNSServerOptions has no inet6_range field", serverClass.contains("public String inet6_range;"))
        assertFalse("DNSServerOptions still declares address", serverClass.contains("public String address;"))
        assertFalse("DNSServerOptions still declares address_resolver", serverClass.contains("address_resolver"))
        assertFalse("DNSServerOptions still declares address_strategy", serverClass.contains("address_strategy"))
        assertFalse("DNSServerOptions still declares address_fallback_delay", serverClass.contains("address_fallback_delay"))
        assertFalse("DNSServerOptions still declares strategy", serverClass.contains("public String strategy;"))

        val builder = readSource("src/main/java/io/nekohasekai/sagernet/fmt/ConfigBuilder.kt")
        assertFalse("ConfigBuilder still emits rcode://success", builder.contains("rcode://success"))
        assertFalse("ConfigBuilder still sets address on a DNS server", builder.contains("address = \"local\""))
        assertFalse("ConfigBuilder still sets address_resolver", builder.contains("address_resolver"))
        assertTrue("ConfigBuilder does not build a local DNS server", builder.contains("type = \"local\""))
        assertTrue("ConfigBuilder does not build a fakeip DNS server", builder.contains("type = \"fakeip\""))
        assertTrue("ConfigBuilder does not set domain_resolver", builder.contains("domain_resolver"))
    }

    @Test
    fun `DNS has no legacy fakeip object`() {
        // The top-level `dns.fakeip` object was removed in 1.14.0; fakeip
        // is now a server of type "fakeip". This was the exact error the
        // first device test hit.
        val builder = readSource("src/main/java/io/nekohasekai/sagernet/fmt/ConfigBuilder.kt")
        assertFalse("ConfigBuilder still sets dns.fakeip", builder.contains("dns.fakeip"))
        val options = readSource("src/main/java/moe/matsuri/nb4a/SingBoxOptions.java")
        assertFalse("SingBoxOptions still declares DNSFakeIPOptions", options.contains("DNSFakeIPOptions"))
        assertFalse("DNSOptions still declares a fakeip field", options.contains("public DNSFakeIPOptions fakeip;"))
    }

    @Test
    fun `DNS block rule uses the predefined action`() {
        // There is no rcode transport in 1.14: a rule that used to route
        // to the rcode://success server must use action "predefined" with
        // no rcode, which answers empty NOERROR.
        val builder = readSource("src/main/java/io/nekohasekai/sagernet/fmt/ConfigBuilder.kt")
        assertFalse("ConfigBuilder still routes a DNS rule to dns-block", builder.contains("server = \"dns-block\""))
        assertTrue("ConfigBuilder has no predefined DNS rule action", builder.contains("action = \"predefined\""))
        val options = readSource("src/main/java/moe/matsuri/nb4a/SingBoxOptions.java")
        val ruleClass = options.substringAfter("public static class DNSRule_DefaultOptions")
        assertTrue("DNSRule_DefaultOptions has no action field", ruleClass.contains("public String action;"))
        assertTrue("DNSRule_DefaultOptions has no strategy field", ruleClass.contains("public String strategy;"))
    }

    @Test
    fun `DNS strategy moved to the top level and the direct rules`() {
        // Server-level `strategy` is gone in 1.14: the final server's
        // strategy becomes top-level dns.strategy, and rules routing to
        // dns-direct carry their own strategy.
        val builder = readSource("src/main/java/io/nekohasekai/sagernet/fmt/ConfigBuilder.kt")
        assertTrue("ConfigBuilder does not set top-level dns.strategy", builder.contains("dns.strategy = remoteDnsStrategy"))
        val loopback = builder.substringAfter("outbound = mutableListOf(\"any\")")
            .substringBefore("})")
        assertTrue("Loopback DNS rule carries no strategy", loopback.contains("strategy ="))
    }

    @Test
    fun `DNS server address parses to 1-14 fields`() {
        // The new DNS server format splits the old `address` string into
        // type / server / server_port / path. Bare hosts are UDP.
        val bare = parseDnsServerAddress("8.8.8.8")
        assertEquals("udp", bare.type)
        assertEquals("8.8.8.8", bare.server)
        assertEquals(null, bare.serverPort)
        assertEquals(null, bare.path)
        val https = parseDnsServerAddress("https://dns.google/dns-query")
        assertEquals("https", https.type)
        assertEquals("dns.google", https.server)
        assertEquals(null, https.serverPort)
        assertEquals("/dns-query", https.path)
        val tlsPort = parseDnsServerAddress("tls://1.1.1.1:853")
        assertEquals("tls", tlsPort.type)
        assertEquals("1.1.1.1", tlsPort.server)
        assertEquals(853, tlsPort.serverPort)
        val tcp = parseDnsServerAddress("tcp://1.1.1.1")
        assertEquals("tcp", tcp.type)
        assertEquals("1.1.1.1", tcp.server)
        val dhcp = parseDnsServerAddress("dhcp://auto")
        assertEquals("dhcp", dhcp.type)
        assertEquals(null, dhcp.server)
        val dhcpIface = parseDnsServerAddress("dhcp://eth0")
        assertEquals("dhcp", dhcpIface.type)
        assertEquals("eth0", dhcpIface.iface)
        val h3 = parseDnsServerAddress("h3://1.1.1.1/dns-query")
        assertEquals("h3", h3.type)
        assertEquals("/dns-query", h3.path)
    }

    @Test
    fun `Mixed inbound does not set sniff or domain_strategy`() {
        // 1.14's mixed inbound no longer accepts sniff / domain_strategy
        // (the struct does not embed them); they move to route rules.
        val builder = readSource("src/main/java/io/nekohasekai/sagernet/fmt/ConfigBuilder.kt")
        val mixed = builder.substringAfter("Inbound_MixedOptions().apply {")
            .substringBefore("})")
        assertFalse("Mixed inbound still sets domain_strategy", mixed.contains("domain_strategy"))
        assertFalse("Mixed inbound still sets sniff", mixed.contains("sniff"))
        assertTrue("No resolve route rule for the mixed inbound", builder.contains("action = \"resolve\""))
        assertTrue("No sniff route rule for the mixed inbound", builder.contains("action = \"sniff\""))
        assertTrue("Resolve/sniff rules do not target the mixed inbound", builder.contains("mutableListOf(TAG_MIXED)"))
    }

    @Test
    fun `Tun inbound uses the 1-14 address field`() {
        // 1.14 merged inet4/inet6_address into `address`; the legacy fields
        // are rejected at service creation (not decode), which is exactly
        // the "legacy tun address fields" error the phone reported.
        val options = readSource("src/main/java/moe/matsuri/nb4a/SingBoxOptions.java")
        val tun = options.substringAfter("public static class TunInboundOptions")
            .substringBefore("public static class TunPlatformOptions")
        assertTrue("TunInboundOptions has no address field", tun.contains("public List<String> address;"))
        assertFalse("TunInboundOptions still declares inet4_address", tun.contains("inet4_address"))
        assertFalse("TunInboundOptions still declares inet6_address", tun.contains("inet6_address"))
        assertFalse("TunInboundOptions still declares endpoint_independent_nat", tun.contains("endpoint_independent_nat"))
        val builder = readSource("src/main/java/io/nekohasekai/sagernet/fmt/ConfigBuilder.kt")
        val tunBlock = builder.substringAfter("Inbound_TunOptions().apply {")
            .substringBefore("})")
        assertTrue("TUN inbound does not set address", tunBlock.contains("address = "))
        assertFalse("TUN inbound still sets inet4_address", tunBlock.contains("inet4_address"))
        assertFalse("TUN inbound still sets inet6_address", tunBlock.contains("inet6_address"))
        assertFalse("TUN inbound still sets endpoint_independent_nat", tunBlock.contains("endpoint_independent_nat"))
        assertFalse("TUN inbound still sets domain_strategy", tunBlock.contains("domain_strategy"))
        assertFalse("TUN inbound still sets sniff", tunBlock.contains("sniff"))
    }

    @Test
    fun `Tun sniff and domain strategy moved to route rules`() {
        // Same migration as the mixed inbound: sniff / domain_strategy are
        // legacy inbound fields, rejected at service creation in 1.13.
        val builder = readSource("src/main/java/io/nekohasekai/sagernet/fmt/ConfigBuilder.kt")
        assertTrue("No resolve/sniff rule covers tun-in", builder.contains("sniffInbounds.add(0, \"tun-in\")"))
        assertTrue("Resolve/sniff rules do not use the shared inbound list", builder.contains("inbound = sniffInbounds"))
    }

    @Test
    fun `Subscription update handles null response`() {
        // execute() can return null with no error (doH3Direct timeout
        // with no recorded error); RawUpdater must fail clearly instead
        // of NPEing on response.contentString.
        val rawUpdater = readSource("src/main/java/io/nekohasekai/sagernet/group/RawUpdater.kt")
        val executeAt = rawUpdater.indexOf(".execute()")
        val guardAt = rawUpdater.indexOf("?: error(\"empty response\")")
        assertTrue("No null check after execute()", executeAt >= 0 && guardAt > executeAt)
    }

    @Test
    fun `Direct outbound is non-empty for 1-14 detours`() {
        // 1.14 rejects a detour to a plain direct outbound at Start
        // ("detour to an empty direct outbound makes no sense"), and the
        // DNS servers detour to direct. The direct outbound must carry a
        // dialer option; udp_fragment=true is behavior-identical to the
        // default (UDPFragmentDefault is true), it only flips isEmpty.
        val builder = readSource("src/main/java/io/nekohasekai/sagernet/fmt/ConfigBuilder.kt")
        assertTrue("DNS servers no longer detour to direct", builder.contains("detour = TAG_DIRECT"))
        assertTrue("Direct outbound is still empty", builder.contains("_hack_config_map[\"udp_fragment\"] = true"))
    }

    @Test
    fun `OpenConnect link parses`() {
        val bean = parseOpenConnect("openconnect://vpn.example.com?flavor=gp&username=u&auth-group=grp#MyOC")
        assertEquals("vpn.example.com", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals("gp", bean.flavor)
        assertEquals("u", bean.username)
        assertEquals("grp", bean.authGroup)
        assertEquals("MyOC", bean.name)
    }

    @Test
    fun `OpenVPN link parses with peer fingerprint`() {
        // The fingerprint rides in the link (a single hex string); without
        // it the tls block would be empty and the core rejects the endpoint.
        val bean = parseOpenVPN("openvpn://testuser:testpass@192.0.2.1:1194?peer-fingerprint=e50e529b08f1e940a8349e33f4ca7652c681880e25e23171475f17fc11249c44#Test-OpenVPN")
        assertEquals("192.0.2.1", bean.serverAddress)
        assertEquals(1194, bean.serverPort)
        assertEquals("testuser", bean.username)
        assertEquals("testpass", bean.password)
        assertEquals("e50e529b08f1e940a8349e33f4ca7652c681880e25e23171475f17fc11249c44", bean.peerFingerprint)
        assertEquals("tls", bean.mode)
        assertEquals("Test-OpenVPN", bean.name)
    }

    @Test
    fun `OpenVPN endpoint uses the 1-14 client type and always emits tls`() {
        // sing-box 1.14 registers the client endpoint as "openvpn-client";
        // "openvpn" alone is an unknown endpoint type at decode. And the
        // client requires a non-nil tls object in tls mode, while the core
        // decodes an empty tls object as nil -- so the builder must emit
        // tls in tls mode (not only when certs are set) and fill the
        // documented default remote_certificate_tls="server" when the
        // object would otherwise be empty.
        val fmt = readSource("src/main/java/io/nekohasekai/sagernet/fmt/openvpn/OpenVPNFmt.kt")
        assertTrue("OpenVPN builder still uses the unknown type", fmt.contains("type = \"openvpn-client\""))
        assertFalse("OpenVPN builder still emits the bare type", fmt.contains("type = \"openvpn\""))
        assertTrue("OpenVPN builder does not emit tls in tls mode", fmt.contains("if (bean.mode != \"static_key\")"))
        assertFalse("OpenVPN builder still gates tls on certs", fmt.contains("needTls"))
        assertTrue("OpenVPN builder has no tls filler", fmt.contains("remote_certificate_tls = \"server\""))
        assertTrue("OpenVPN parser does not read peer-fingerprint", fmt.contains("queryParameter(\"peer-fingerprint\")"))
        val options = readSource("src/main/java/moe/matsuri/nb4a/SingBoxOptions.java")
        val tlsClass = options.substringAfter("public static class OpenVPNOutboundTLSOptions")
            .substringBefore("public static class OpenVPNControlWrapOptions")
        assertTrue("TLS options class has no remote_certificate_tls field", tlsClass.contains("public String remote_certificate_tls;"))
    }

    @Test
    fun `NewDialer outbounds name a domain resolver`() {
        // Naive hardcodes RemoteIsDomain=true and NewDialer=true
        // (protocol/naive/outbound.go), and OpenVPN/OpenConnect do the same
        // for domain servers (protocol/openvpn/client.go,
        // protocol/openconnect/client.go). With 2+ DNS servers in the
        // config and no resolver, dialer.NewDNSQueryOptions refuses to
        // return empty options for a new dialer -- the phone reported
        // "create service: initialize outbound[0]: missing domain resolver
        // for domain server address" for Naive. The fix names dns-direct
        // (always present in the generated config) as the resolver, via
        // the _hack_config_map the app already uses for flat DialerOptions
        // fields (domain_strategy, udp_fragment). For IP servers the
        // resolver is never consulted; for domain servers it resolves via
        // the direct DNS, which is correct for a proxy server address.
        for (path in listOf(
            "src/main/java/io/nekohasekai/sagernet/fmt/naive/NaiveFmt.kt",
            "src/main/java/io/nekohasekai/sagernet/fmt/openvpn/OpenVPNFmt.kt",
            "src/main/java/io/nekohasekai/sagernet/fmt/openconnect/OpenConnectFmt.kt",
        )) {
            val fmt = readSource(path)
            assertTrue("$path does not name a domain resolver", fmt.contains("_hack_config_map[\"domain_resolver\"] = \"dns-direct\""))
        }
    }

    @Test
    fun `new protocol settings activities are declared in the manifest`() {
        // The phone crashed with ActivityNotFoundException when opening
        // the Snell/OpenVPN/OpenConnect editors (new and edit alike):
        // the three SettingsActivity classes exist but were never added
        // to AndroidManifest.xml, so the system cannot start them.
        val manifest = readSource("src/main/AndroidManifest.xml")
        for (activity in listOf(
            "io.nekohasekai.sagernet.ui.profile.SnellSettingsActivity",
            "io.nekohasekai.sagernet.ui.profile.OpenVPNSettingsActivity",
            "io.nekohasekai.sagernet.ui.profile.OpenConnectSettingsActivity",
        )) {
            assertTrue("Manifest does not declare $activity", manifest.contains("android:name=\"$activity\""))
        }
    }

    @Test
    fun `database version is 8 with a migration from 7`() {
        // v1.4.29 removes the trojan-go and mieru protocols entirely,
        // including their trojanGoBean / mieruBean BLOB columns. The
        // columns go away, so the version goes to 8 with a manual
        // migration that also deletes any rows of those two types:
        // requireBean() throws on an unknown type, so a stale row
        // would crash the profile list on open.
        val db = readSource("src/main/java/io/nekohasekai/sagernet/database/SagerDatabase.kt")
        assertTrue("Database version is not 8", db.contains("version = 8"))
        assertTrue("No MIGRATION_7_8 registered", db.contains("MIGRATION_7_8"))
    }
}
