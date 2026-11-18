package io.nekohasekai.sagernet.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the v1.4.36 batch: the empty-group TCPing /
 * ICMPing toast is a single unified message that names no specific
 * protocols (user decision 2026-11-16).
 *
 * Same file-parsing style as the other tests in this package: the
 * production code touches Android APIs, so the tests assert on the
 * source text instead of running it.
 */
class Fixes1436Test {

    private fun readSource(relativePath: String): String {
        val file = File(relativePath)
        assertTrue("source file missing: $relativePath", file.exists())
        return file.readText()
    }

    private fun stringValue(xml: String, name: String): String {
        val start = xml.indexOf("name=\"$name\"")
        assertTrue("string $name missing", start >= 0)
        val open = xml.indexOf('>', start)
        val close = xml.indexOf("</string>", open)
        assertTrue("string $name has no closing tag", open >= 0 && close > open)
        return xml.substring(open + 1, close)
    }

    // NOTE: version pin retired in v1.4.37; now pinned by Fixes1437Test.

    @Test
    fun `no testable nodes copy names no protocols`() {
        val banned = listOf("tuic", "hysteria", "wireguard", "openvpn", "openconnect", "自定义配置", "custom config")
        for (name in listOf(
            "connection_test_no_testable_nodes_tcp",
            "connection_test_no_testable_nodes_icmp"
        )) {
            for (path in listOf(
                "src/main/res/values/strings.xml",
                "src/main/res/values-zh-rCN/strings.xml",
                "src/main/res/values-ru/strings.xml"
            )) {
                val value = stringValue(readSource(path), name)
                for (bad in banned) {
                    assertTrue("$name in $path names a protocol: $bad", !value.lowercase().contains(bad.lowercase()))
                }
            }
        }
    }

    @Test
    fun `no testable nodes copy is the unified message`() {
        val expected = mapOf(
            "src/main/res/values/strings.xml" to mapOf(
                "connection_test_no_testable_nodes_tcp" to "No nodes in this group support TCPing",
                "connection_test_no_testable_nodes_icmp" to "No nodes in this group support ICMPing"
            ),
            "src/main/res/values-zh-rCN/strings.xml" to mapOf(
                "connection_test_no_testable_nodes_tcp" to "该分组没有可 TCPing 的节点",
                "connection_test_no_testable_nodes_icmp" to "该分组没有可 ICMPing 的节点"
            ),
            "src/main/res/values-ru/strings.xml" to mapOf(
                "connection_test_no_testable_nodes_tcp" to "В этой группе нет узлов, поддерживающих TCPing",
                "connection_test_no_testable_nodes_icmp" to "В этой группе нет узлов, поддерживающих ICMPing"
            )
        )
        for ((path, strings) in expected) {
            val xml = readSource(path)
            for ((name, value) in strings) {
                assertTrue(
                    "$name in $path is not the unified message",
                    stringValue(xml, name) == value
                )
            }
        }
    }

    @Test
    fun `empty group copy is unchanged`() {
        val expected = mapOf(
            "src/main/res/values/strings.xml" to "This group has no nodes",
            "src/main/res/values-zh-rCN/strings.xml" to "该分组没有节点",
            "src/main/res/values-ru/strings.xml" to "В этой группе нет узлов"
        )
        for ((path, value) in expected) {
            assertTrue(
                "connection_test_empty_group in $path changed",
                stringValue(readSource(path), "connection_test_empty_group") == value
            )
        }
    }
}
