package io.nekohasekai.sagernet.fmt.openvpn

import io.nekohasekai.sagernet.ktx.linkBuilder
import io.nekohasekai.sagernet.ktx.toLink
import io.nekohasekai.sagernet.ktx.urlSafe
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.listByLineOrComma
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

// openvpn://<host>:<port>?network=udp&mode=tls&username=<u>&password=<p>&cipher=<c>&auth=<a>&peer-fingerprint=<hex>#<name>
// Certificates and keys are too large for a link; they are filled in the editor.
// A peer fingerprint is a single hex string, so it rides along in the link:
// sing-box requires a CA certificate or a peer fingerprint in tls mode.
fun parseOpenVPN(link: String): OpenVPNBean {
    val url = link.replace("openvpn://", "https://").toHttpUrlOrNull()
        ?: error("Invalid openvpn link: $link")
    return OpenVPNBean().apply {
        serverAddress = url.host
        serverPort = url.port
        username = url.username.ifBlank { url.queryParameter("username") ?: "" }
        password = url.password.ifBlank { url.queryParameter("password") ?: "" }
        name = url.fragment ?: ""
        url.queryParameter("network")?.let { network = it }
        url.queryParameter("mode")?.let { mode = it }
        url.queryParameter("cipher")?.let { cipher = it }
        url.queryParameter("auth")?.let { auth = it }
        url.queryParameter("mtu")?.toIntOrNull()?.let { mtu = it }
        url.queryParameter("peer-fingerprint")?.let { peerFingerprint = it }
        initializeDefaultValues()
    }
}

fun OpenVPNBean.toUri(): String {
    val builder = linkBuilder().host(serverAddress).port(serverPort)
    if (username.isNotBlank()) {
        builder.username(username)
        if (password.isNotBlank()) builder.password(password)
    }
    if (network != "udp") builder.addQueryParameter("network", network)
    if (mode != "tls") builder.addQueryParameter("mode", mode)
    if (cipher.isNotBlank()) builder.addQueryParameter("cipher", cipher)
    if (auth.isNotBlank()) builder.addQueryParameter("auth", auth)
    if (mtu > 0) builder.addQueryParameter("mtu", "$mtu")
    if (peerFingerprint.isNotBlank()) builder.addQueryParameter("peer-fingerprint", peerFingerprint)
    if (name.isNotBlank()) builder.encodedFragment(name.urlSafe())
    return builder.toLink("openvpn")
}

fun buildSingBoxEndpointOpenVPNBean(bean: OpenVPNBean): SingBoxOptions.Endpoint_OpenVPNOptions {
    return SingBoxOptions.Endpoint_OpenVPNOptions().apply {
        // sing-box 1.14 registers the client endpoint as "openvpn-client"
        // (constant.TypeOpenVPNClient); "openvpn" alone is not a known type.
        type = "openvpn-client"
        // OpenVPN also builds its dialer with NewDialer=true
        // (protocol/openvpn/client.go): a domain server with 2+ DNS
        // servers and no resolver fails init the same way Naive did, so
        // name dns-direct as the resolver (ignored for IP servers).
        _hack_config_map["domain_resolver"] = "dns-direct"
        server = bean.serverAddress
        server_port = bean.serverPort
        network = bean.network
        if (bean.mode != "tls") mode = bean.mode
        if (bean.username.isNotBlank()) username = bean.username
        if (bean.password.isNotBlank()) password = bean.password
        if (bean.address.isNotBlank()) address = bean.address.listByLineOrComma()
        if (bean.cipher.isNotBlank()) cipher = bean.cipher
        if (bean.dataCiphers.isNotBlank()) data_ciphers = bean.dataCiphers.listByLineOrComma()
        if (bean.auth.isNotBlank()) auth = bean.auth
        if (bean.mtu > 0) mtu = bean.mtu
        if (bean.remoteRandom) remote_random = true
        if (bean.routeNoPull) route_no_pull = true
        if (bean.redirectGateway) redirect_gateway = true
        if (bean.mode == "static_key") {
            if (bean.staticKey.isNotBlank()) static_key = listOf(bean.staticKey)
            if (bean.keyDirection.isNotBlank()) key_direction = bean.keyDirection
        }
        // sing-box 1.14 requires a `tls` object in tls mode (protocol/openvpn
        // client.go rejects a missing one), even when no custom certs are set:
        // the object itself is the switch, the fields inside are optional.
        if (bean.mode != "static_key") {
            tls = SingBoxOptions.OpenVPNOutboundTLSOptions().apply {
                if (bean.caCert.isNotBlank()) certificate = listOf(bean.caCert)
                if (bean.clientCert.isNotBlank()) client_certificate = listOf(bean.clientCert)
                if (bean.clientKey.isNotBlank()) client_key = listOf(bean.clientKey)
                if (bean.peerFingerprint.isNotBlank()) peer_fingerprint = bean.peerFingerprint.listByLineOrComma()
                if (bean.controlWrapType.isNotBlank()) {
                    control_wrap = SingBoxOptions.OpenVPNControlWrapOptions().apply {
                        type = bean.controlWrapType
                        if (bean.controlWrapKey.isNotBlank()) key = listOf(bean.controlWrapKey)
                        if (bean.controlWrapDirection.isNotBlank()) direction = bean.controlWrapDirection
                    }
                }
                // sing-box decodes an empty `tls` object as nil, but the
                // client requires a non-nil tls in tls mode. Fill the
                // documented default so the object survives decoding;
                // "server" is what the core uses when the field is absent.
                if (bean.caCert.isBlank() && bean.clientCert.isBlank() &&
                    bean.clientKey.isBlank() && bean.peerFingerprint.isBlank() &&
                    bean.controlWrapType.isBlank()) {
                    remote_certificate_tls = "server"
                }
            }
        }
    }
}
