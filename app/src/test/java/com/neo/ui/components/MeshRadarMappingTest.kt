package com.neo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshRadarMappingTest {

    @Test
    fun `empty inputs produce no nodes`() {
        val nodes = meshNodesFrom(connectedPeers = emptyList(), dhtPeerCount = 0)
        assertTrue(nodes.isEmpty())
    }

    @Test
    fun `connected peers map to non-lost states`() {
        val nodes = meshNodesFrom(connectedPeers = listOf("peerA", "peerB", "peerC"), dhtPeerCount = 0)
        assertEquals(3, nodes.size)
        assertTrue(nodes.none { it.state == MeshNodeState.LOST })
        assertTrue(nodes.none { it.state == MeshNodeState.DISCOVERABLE })
        assertTrue(nodes.all { it.state in setOf(MeshNodeState.CONNECTED, MeshNodeState.RELAY, MeshNodeState.WEAK) })
    }

    @Test
    fun `mapping is deterministic for the same input`() {
        val a = meshNodesFrom(listOf("alpha", "beta"), dhtPeerCount = 3)
        val b = meshNodesFrom(listOf("alpha", "beta"), dhtPeerCount = 3)
        assertEquals(a.size, b.size)
        a.zip(b).forEach { (x, y) ->
            assertEquals(x.id, y.id)
            assertEquals(x.state, y.state)
            assertEquals(x.angle, y.angle, 1e-6f)
            assertEquals(x.signal, y.signal, 1e-6f)
        }
    }

    @Test
    fun `dht-only peers appear as discoverable on outer band`() {
        val nodes = meshNodesFrom(connectedPeers = emptyList(), dhtPeerCount = 5)
        assertEquals(5, nodes.size)
        assertTrue(nodes.all { it.state == MeshNodeState.DISCOVERABLE })
        // Outer band → low signal (positioned near the outer rings).
        assertTrue(nodes.all { it.signal < 0.4f })
    }

    @Test
    fun `discoverable count is capped to guard density`() {
        val nodes = meshNodesFrom(connectedPeers = listOf("p1"), dhtPeerCount = 1000, maxDiscoverable = 14)
        val discoverable = nodes.count { it.state == MeshNodeState.DISCOVERABLE }
        assertEquals(14, discoverable)
        assertEquals(15, nodes.size) // 1 connected + 14 capped discoverable
    }

    @Test
    fun `signal values stay within unit range`() {
        val nodes = meshNodesFrom(listOf("x", "y", "z"), dhtPeerCount = 8)
        assertTrue(nodes.all { it.signal in 0f..1f })
    }
}
