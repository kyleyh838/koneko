package io.nekohasekai.sagernet.fmt.naive

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.ktx.*
import moe.matsuri.nb4a.SingBoxOptions
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

fun parseNaive(link: String): NaiveBean {
    val proto = link.substringAfter("+").substringBefore(":")
    val url = ("https://" + link.substringAfter("://")).toHttpUrlOrNull()
        ?: error("Invalid naive link: $link")
    return NaiveBean().also {
        it.proto = proto
    }.apply {
        serverAddress = url.host
        serverPort = url.port
        username = url.username
        password = url.password
        sni = url.queryParameter("sni")
        certificates = url.queryParameter("cert")
        extraHeaders = url.queryParameter("extra-headers")?.unUrlSafe()?.replace("\r\n", "\n")
        insecureConcurrency = url.queryParameter("insecure-concurrency")?.toIntOrNull()
        name = url.fragment
        initializeDefaultValues()
    }
}

fun NaiveBean.toUri(proxyOnly: Boolean = false): String {
    val builder = linkBuilder().host(finalAddress).port(finalPort)
    if (username.isNotBlank()) {
        builder.username(username)
        if (password.isNotBlank()) {
            builder.password(password)
        }
    }
    if (!proxyOnly) {
        if (sni.isNotBlank()) {
            builder.addQueryParameter("sni", sni)
        }
        if (certificates.isNotBlank()) {
            builder.addQueryParameter("cert", certificates)
        }
        if (extraHeaders.isNotBlank()) {
            builder.addQueryParameter("extra-headers", extraHeaders)
        }
        if (name.isNotBlank()) {
            builder.encodedFragment(name.urlSafe())
        }
        if (insecureConcurrency > 0) {
            builder.addQueryParameter("insecure-concurrency", "$insecureConcurrency")
        }
    }
    return builder.toLink(if (proxyOnly) proto else "naive+$proto", false)
}

fun buildSingBoxOutboundNaiveBean(bean: NaiveBean): SingBoxOptions.Outbound_NaiveOptions {
    // sing-box 1.14 ships NaiveProxy in the core: no external binary needed.
    // The bean's proto is https or quic; the outbound takes a quic boolean
    // and a TLS object instead.
    return SingBoxOptions.Outbound_NaiveOptions().apply {
        type = "naive"
        // Naive hardcodes RemoteIsDomain=true and NewDialer=true
        // (protocol/naive/outbound.go). With 2+ DNS servers in the config
        // and no resolver, the core refuses to build its dialer: "missing
        // domain resolver for domain server address". Name dns-direct
        // (always present in the generated config) as the resolver; for IP
        // servers it is never consulted, for domain servers it resolves
        // via the direct DNS. Same _hack_config_map mechanism the app
        // already uses for flat DialerOptions fields.
        _hack_config_map["domain_resolver"] = "dns-direct"
        server = bean.serverAddress
        server_port = bean.serverPort
        username = bean.username
        password = bean.password
        if (bean.insecureConcurrency > 0) insecure_concurrency = bean.insecureConcurrency
        if (bean.extraHeaders.isNotBlank()) extra_headers = parseExtraHeaders(bean.extraHeaders)
        quic = bean.proto == "quic"
        if (bean.proto != "http") {
            tls = SingBoxOptions.OutboundTLSOptions().apply {
                enabled = true
                insecure = DataStore.globalAllowInsecure
                if (bean.sni.isNotBlank()) server_name = bean.sni
                if (bean.certificates.isNotBlank()) certificate = bean.certificates
            }
        }
    }
}

private fun parseExtraHeaders(raw: String): Map<String, List<String>> {
    // Stored as "Name: value" lines (see parseNaive); the core wants a
    // header name -> values map.
    val map = LinkedHashMap<String, MutableList<String>>()
    raw.split("\n").forEach { line ->
        val idx = line.indexOf(':')
        if (idx > 0) {
            val name = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim()
            if (name.isNotEmpty() && value.isNotEmpty()) {
                map.getOrPut(name) { mutableListOf() }.add(value)
            }
        }
    }
    return map
}

fun NaiveBean.buildNaiveConfig(port: Int): String {
    return JSONObject().apply {
        // process ipv6
        finalAddress = finalAddress.wrapIPV6Host()
        serverAddress = serverAddress.wrapIPV6Host()

        // process sni
        if (sni.isNotBlank()) {
            put("host-resolver-rules", "MAP $sni $finalAddress")
            finalAddress = sni
        } else {
            if (serverAddress.isIpAddress()) {
                // for naive, using IP as SNI name hardly happens
                // and host-resolver-rules cannot resolve the SNI problem
                // so do nothing
            } else {
                put("host-resolver-rules", "MAP $serverAddress $finalAddress")
                finalAddress = serverAddress
            }
        }

        put("listen", "socks://$LOCALHOST:$port")
        put("proxy", toUri(true))
        if (extraHeaders.isNotBlank()) {
            put("extra-headers", extraHeaders.split("\n").joinToString("\r\n"))
        }
        if (DataStore.logLevel > 0) {
            put("log", "")
        }
        if (insecureConcurrency > 0) {
            put("insecure-concurrency", insecureConcurrency)
        }
    }.toStringPretty()
}