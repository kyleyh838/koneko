package io.nekohasekai.sagernet.ui

import io.nekohasekai.sagernet.fmt.wireguard.parseWireGuardLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real behavior tests for the wireguard:// link parser added in
 * v1.4.37 (#1035). Unlike the file-parsing tests, these run the parser
 * on the JVM: the bean is a plain data holder, so class loading works
 * without Android.
 */
class WireGuardLinkParseTest {

    @Test
    fun `parses full link with slash key`() {
        val bean = parseWireGuardLink(
            "wireguard://abc/def+ghi0123456789ABCDEFGHIJKLMNOPQ==@1.2.3.4:51820/" +
                "?publickey=PUBKEYHERE&presharedkey=PSK123" +
                "&address=10.0.0.2/32,fd00::2/128&mtu=1400&reserved=AAAAAA==&keepalive=25#WG-Test"
        )
        assertEquals("1.2.3.4", bean.serverAddress)
        assertEquals(51820, bean.serverPort)
        assertEquals("abc/def+ghi0123456789ABCDEFGHIJKLMNOPQ==", bean.privateKey)
        assertEquals("PUBKEYHERE", bean.peerPublicKey)
        assertEquals("PSK123", bean.peerPreSharedKey)
        assertEquals("10.0.0.2/32\nfd00::2/128", bean.localAddress)
        assertEquals(1400, bean.mtu)
        assertEquals("AAAAAA==", bean.reserved)
        assertEquals(25, bean.persistentKeepalive)
        assertEquals("WG-Test", bean.name)
    }

    @Test
    fun `missing presharedkey is blank`() {
        val bean = parseWireGuardLink(
            "wireguard://privkey@1.2.3.4:51820/?publickey=PUBKEYHERE#NoPSK"
        )
        assertTrue(bean.peerPreSharedKey.isNullOrBlank())
        assertEquals("PUBKEYHERE", bean.peerPublicKey)
    }

    @Test(expected = IllegalStateException::class)
    fun `missing publickey errors`() {
        parseWireGuardLink("wireguard://privkey@1.2.3.4:51820/")
    }
}
