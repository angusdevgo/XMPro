package teikengstudio.xmpro.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackQueueBridgeTest {

    @Test
    fun `first item cannot go back`() {
        val snapshot = PlaybackQueueBridge.Snapshot(index = 0, size = 5)
        assertFalse(snapshot.hasPrevious)
        assertTrue(snapshot.hasNext)
    }

    @Test
    fun `last item cannot go forward`() {
        val snapshot = PlaybackQueueBridge.Snapshot(index = 4, size = 5)
        assertTrue(snapshot.hasPrevious)
        assertFalse(snapshot.hasNext)
    }

    @Test
    fun `a middle item can go both ways`() {
        val snapshot = PlaybackQueueBridge.Snapshot(index = 2, size = 5)
        assertTrue(snapshot.hasPrevious)
        assertTrue(snapshot.hasNext)
    }

    @Test
    fun `an empty queue has nowhere to go`() {
        val snapshot = PlaybackQueueBridge.Snapshot(index = 0, size = 0)
        assertFalse(snapshot.hasPrevious)
        assertFalse(snapshot.hasNext)
    }

    @Test
    fun `a single item queue offers neither direction`() {
        val snapshot = PlaybackQueueBridge.Snapshot(index = 0, size = 1)
        assertFalse(snapshot.hasPrevious)
        assertFalse(snapshot.hasNext)
    }

    @Test
    fun `published snapshots are readable by the service`() {
        PlaybackQueueBridge.publish(PlaybackQueueBridge.Snapshot(index = 3, size = 9))
        assertEquals(3, PlaybackQueueBridge.queue.value.index)
        assertEquals(9, PlaybackQueueBridge.queue.value.size)
    }
}
