package io.nekohasekai.sagernet.fmt.snell

import io.nekohasekai.sagernet.ktx.linkBuilder
import io.nekohasekai.sagernet.ktx.toLink
import io.nekohasekai.sagernet.ktx.urlSafe
import moe.matsuri.nb4a.SingBoxOptions
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

// snell://<psk>@<host>:<port>?version=4&obfs=http&obfs-host=<host>&userkey=<key>&reuse=1&mode=default#<name>
fun parseSnell(link: String): SnellBean {
    val url = link.replace("snell://", "https://").toHttpUrlOrNull()
        ?: error("Invalid snell link: $link")
    return SnellBean().apply {
        serverAddress = url.host
        serverPort = url.port
        psk = url.username
        name = url.fragment ?: ""
        url.queryParameter("version")?.toIntOrNull()?.let { version = it }
        url.queryParameter("obfs")?.let { obfsMode = it }
        url.queryParameter("obfs-host")?.let { obfsHost = it }
        url.queryParameter("userkey")?.let { userKey = it }
        url.queryParameter("reuse")?.let { reuse = it == "1" }
        url.queryParameter("mode")?.let { v6Mode = it }
        initializeDefaultValues()
    }
}

fun SnellBean.toUri(): String {
    val builder = linkBuilder().username(psk).host(serverAddress).port(serverPort)
    builder.addQueryParameter("version", "$version")
    if (version == 4) {
        if (obfsMode != "none") {
            builder.addQueryParameter("obfs", obfsMode)
            if (obfsHost.isNotBlank()) builder.addQueryParameter("obfs-host", obfsHost)
        }
    } else {
        if (userKey.isNotBlank()) builder.addQueryParameter("userkey", userKey)
        if (v6Mode != "default") builder.addQueryParameter("mode", v6Mode)
    }
    if (reuse) builder.addQueryParameter("reuse", "1")
    if (name.isNotBlank()) builder.encodedFragment(name.urlSafe())
    return builder.toLink("snell")
}

fun buildSingBoxOutboundSnellBean(bean: SnellBean): SingBoxOptions.Outbound_SnellOptions {
    return SingBoxOptions.Outbound_SnellOptions().apply {
        type = "snell"
        server = bean.serverAddress
        server_port = bean.serverPort
        version = bean.version
        psk = bean.psk
        if (bean.userKey.isNotBlank()) userkey = bean.userKey
        if (bean.reuse) reuse = true
        if (bean.version == 4) {
            if (bean.obfsMode != "none") {
                obfs_mode = bean.obfsMode
                if (bean.obfsHost.isNotBlank()) obfs_host = bean.obfsHost
            }
        } else {
            if (bean.v6Mode != "default") mode = bean.v6Mode
        }
    }
}
