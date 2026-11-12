package teikengstudio.xmpro.player

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Connects the media session with the app's play queue.
 *
 * The service owns a player with a single media item because every episode has
 * to be resolved (and, for paid tracks, debited) through the client before it
 * can play. That leaves the notification and Bluetooth without a queue to walk,
 * so their next/previous buttons are routed back to the view model through this
 * in-process bridge instead of touching the player directly.
 */
object PlaybackQueueBridge {

    enum class Command { Next, Previous }

    data class Snapshot(val index: Int, val size: Int) {
        val hasPrevious: Boolean get() = index > 0
        val hasNext: Boolean get() = index in 0 until size - 1
    }

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 8)
    val commands: SharedFlow<Command> = _commands.asSharedFlow()

    private val _queue = MutableStateFlow(Snapshot(index = 0, size = 0))
    val queue: StateFlow<Snapshot> = _queue.asStateFlow()

    fun publish(snapshot: Snapshot) {
        _queue.value = snapshot
    }

    fun request(command: Command) {
        _commands.tryEmit(command)
    }
}
