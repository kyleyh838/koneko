package io.nekohasekai.sagernet.fmt.openconnect

import io.nekohasekai.sagernet.ktx.linkBuilder
import io.nekohasekai.sagernet.ktx.toLink
import io.nekohasekai.sagernet.ktx.urlSafe
import moe.matsuri.nb4a.SingBoxOptions
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

// openconnect://<host>[:<port>]?flavor=gp&username=<u>&password=<p>&auth-group=<g>&cookie=<c>&token-mode=totp&token-secret=<s>#<name>
fun parseOpenConnect(link: String): OpenConnectBean {
    val url = link.replace("openconnect://", "https://").toHttpUrlOrNull()
        ?: error("Invalid openconnect link: $link")
    return OpenConnectBean().apply {
        serverAddress = url.host
        serverPort = url.port
        username = url.username.ifBlank { url.queryParameter("username") ?: "" }
        password = url.password.ifBlank { url.queryParameter("password") ?: "" }
        name = url.fragment ?: ""
        url.queryParameter("flavor")?.let { flavor = it }
        url.queryParameter("auth-group")?.let { authGroup = it }
        url.queryParameter("cookie")?.let { cookie = it }
        url.queryParameter("token-mode")?.let { tokenMode = it }
        url.queryParameter("token-secret")?.let { tokenSecret = it }
        url.queryParameter("token-pin")?.let { tokenPin = it }
        initializeDefaultValues()
    }
}

fun OpenConnectBean.toUri(): String {
    val builder = linkBuilder().host(serverAddress).port(serverPort)
    if (username.isNotBlank()) {
        builder.username(username)
        if (password.isNotBlank()) builder.password(password)
    }
    if (flavor != "anyconnect") builder.addQueryParameter("flavor", flavor)
    if (authGroup.isNotBlank()) builder.addQueryParameter("auth-group", authGroup)
    if (cookie.isNotBlank()) builder.addQueryParameter("cookie", cookie)
    if (tokenMode.isNotBlank()) {
        builder.addQueryParameter("token-mode", tokenMode)
        if (tokenSecret.isNotBlank()) builder.addQueryParameter("token-secret", tokenSecret)
        if (tokenPin.isNotBlank()) builder.addQueryParameter("token-pin", tokenPin)
    }
    if (name.isNotBlank()) builder.encodedFragment(name.urlSafe())
    return builder.toLink("openconnect")
}

fun buildSingBoxEndpointOpenConnectBean(bean: OpenConnectBean): SingBoxOptions.Endpoint_OpenConnectOptions {
    return SingBoxOptions.Endpoint_OpenConnectOptions().apply {
        type = "openconnect"
        // OpenConnect also builds its dialer with NewDialer=true
        // (protocol/openconnect/client.go): a domain server with 2+ DNS
        // servers and no resolver fails init the same way Naive did, so
        // name dns-direct as the resolver (ignored for IP servers).
        _hack_config_map["domain_resolver"] = "dns-direct"
        // The core takes one server string; the port rides along when it is
        // not the default 443.
        server = if (bean.serverPort != 443) "${bean.serverAddress}:${bean.serverPort}" else bean.serverAddress
        if (bean.flavor != "anyconnect") flavor = bean.flavor
        if (bean.username.isNotBlank()) username = bean.username
        if (bean.password.isNotBlank()) password = bean.password
        if (bean.authGroup.isNotBlank()) auth_group = bean.authGroup
        if (bean.cookie.isNotBlank()) cookie = bean.cookie
        if (bean.tokenMode.isNotBlank()) {
            token = SingBoxOptions.OpenConnectTokenOptions().apply {
                mode = bean.tokenMode
                if (bean.tokenSecret.isNotBlank()) secret = bean.tokenSecret
                if (bean.tokenPin.isNotBlank()) pin = bean.tokenPin
            }
        }
        if (bean.reportedOs.isNotBlank()) reported_os = bean.reportedOs
        if (bean.userAgent.isNotBlank()) user_agent = bean.userAgent
        if (bean.localHostname.isNotBlank()) local_hostname = bean.localHostname
        if (bean.noUdp) no_udp = true
        if (bean.mtu > 0) mtu = bean.mtu
        val needTls = bean.tlsInsecure || bean.tlsServerName.isNotBlank() ||
            bean.caCert.isNotBlank() || bean.clientCert.isNotBlank() ||
            bean.clientKey.isNotBlank()
        if (needTls) {
            tls = SingBoxOptions.OpenConnectTLSOptions().apply {
                if (bean.tlsInsecure) insecure = true
                if (bean.tlsServerName.isNotBlank()) server_name = bean.tlsServerName
                if (bean.caCert.isNotBlank()) certificate_authority = listOf(bean.caCert)
                if (bean.clientCert.isNotBlank()) client_certificate = listOf(bean.clientCert)
                if (bean.clientKey.isNotBlank()) client_key = listOf(bean.clientKey)
            }
        }
    }
}
