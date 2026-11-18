package io.nekohasekai.sagernet.fmt.wireguard

import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.Util
import moe.matsuri.nb4a.utils.listByLineOrComma
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayOutputStream

fun genReserved(anyStr: String): String {
    try {
        val list = anyStr.listByLineOrComma()
        val ba = ByteArray(3)
        if (list.size == 3) {
            list.forEachIndexed { index, s ->
                val i = s
                    .replace("[", "")
                    .replace("]", "")
                    .replace(" ", "")
                    .toIntOrNull() ?: return anyStr
                ba[index] = i.toByte()
            }
            return Util.b64EncodeOneLine(ba)
        } else {
            return anyStr
        }
    } catch (e: Exception) {
        return anyStr
    }
}

// Percent-decode only %XX sequences; '+' stays '+' (unlike URLDecoder,
// which turns '+' into a space and would corrupt a base64 key).
private fun percentDecodeAscii(input: String): String {
    if (!input.contains('%')) return input
    val bytes = ByteArrayOutputStream()
    var i = 0
    while (i < input.length) {
        val c = input[i]
        if (c == '%' && i + 2 < input.length) {
            val v = input.substring(i + 1, i + 3).toIntOrNull(16)
            if (v != null) {
                bytes.write(v)
                i += 3
                continue
            }
        }
        bytes.write(c.code)
        i++
    }
    return bytes.toString(Charsets.UTF_8.name())
}

fun parseWireGuardLink(url: String): WireGuardBean {
    // wireguard://<privateKey>@<host>:<port>/?publickey=...&presharedkey=...&address=...&mtu=...&reserved=...&keepalive=...#<name>
    // HttpUrl silently misparses '/' in the userinfo (it treats it as a
    // path separator), and a base64 private key often contains '/': split
    // the authority off by hand and only let HttpUrl parse the rest.
    val rest = url.removePrefix("wireguard://")
    val at = rest.lastIndexOf('@')
    if (at < 0) error("invalid wireguard link $url")
    val userinfo = percentDecodeAscii(rest.substring(0, at))
    val link = ("https://" + rest.substring(at + 1)).toHttpUrlOrNull()
        ?: error("invalid wireguard link $url")
    return WireGuardBean().apply {
        serverAddress = link.host
        serverPort = link.port
        val hostPort = rest.substring(at + 1).substringBefore('/').substringBefore('?').substringBefore('#')
        if (hostPort.substringAfterLast(':', "").toIntOrNull() == null) {
            error("invalid wireguard link $url")
        }
        name = link.fragment
        privateKey = userinfo.ifBlank { link.queryParameter("privatekey") ?: "" }
        if (privateKey.isBlank()) error("missing private key in wireguard link")
        peerPublicKey = link.queryParameter("publickey") ?: error("missing public key in wireguard link")
        peerPreSharedKey = link.queryParameter("presharedkey") ?: ""
        link.queryParameter("address")?.let {
            localAddress = it.split(",").joinToString("\n")
        }
        link.queryParameter("mtu")?.toIntOrNull()?.let { mtu = it }
        link.queryParameter("reserved")?.let { reserved = it }
        (link.queryParameter("keepalive") ?: link.queryParameter("persistentkeepalive"))?.toIntOrNull()?.let {
            persistentKeepalive = it
        }
    }
}

fun buildSingBoxEndpointWireguardBean(bean: WireGuardBean): SingBoxOptions.Endpoint_WireGuardOptions {
    // sing-box 1.14 removed the wireguard outbound; WireGuard is an endpoint
    // now (option.WireGuardEndpointOptions). The single peer of the old
    // outbound becomes the endpoint's only peer, allowed all traffic.
    return SingBoxOptions.Endpoint_WireGuardOptions().apply {
        type = "wireguard"
        // Upstream sing-box#3390: on mobile data WireGuard throughput
        // collapses (~500 Kbps) unless the endpoint dials via direct.
        // Endpoint_WireGuardOptions has no detour field, so route it
        // through the hack map (same as domain_resolver in OpenVPN).
        _hack_config_map["detour"] = "direct"
        address = bean.localAddress.listByLineOrComma()
        private_key = bean.privateKey
        mtu = bean.mtu
        peers = listOf(SingBoxOptions.WireGuardPeer().apply {
            address = bean.serverAddress
            port = bean.serverPort
            public_key = bean.peerPublicKey
            if (bean.peerPreSharedKey.isNotBlank()) pre_shared_key = bean.peerPreSharedKey
            if (bean.persistentKeepalive != null && bean.persistentKeepalive > 0) persistent_keepalive_interval = bean.persistentKeepalive
            allowed_ips = listOf("0.0.0.0/0", "::/0")
            if (bean.reserved.isNotBlank()) reserved = genReserved(bean.reserved)
        })
    }
}
