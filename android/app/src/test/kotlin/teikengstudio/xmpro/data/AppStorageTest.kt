package teikengstudio.xmpro.data

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppStorageTest {
    private val roots = mutableListOf<File>()

    private fun tempRoot(): File =
        Files.createTempDirectory("xmpro-storage-").toFile().also { roots += it }

    @AfterTest
    fun cleanup() {
        roots.forEach { it.deleteRecursively() }
        roots.clear()
    }

    @Test
    fun `writes and reads state through a transaction`() {
        val root = tempRoot()
        AppStorage.open(root.absolutePath).use { storage ->
            storage.transaction { state ->
                state.put("activeAccountId", "account-1")
                state.getJSONObject("settings").put("showRequestFailureBanner", true)
            }
            assertEquals("account-1", storage.state().getString("activeAccountId"))
        }
        AppStorage.open(root.absolutePath).use { reopened ->
            assertEquals("account-1", reopened.state().getString("activeAccountId"))
            assertTrue(reopened.state().getJSONObject("settings").getBoolean("showRequestFailureBanner"))
        }
    }

    @Test
    fun `refuses login secrets inside persisted state`() {
        val root = tempRoot()
        AppStorage.open(root.absolutePath).use { storage ->
            val failure = assertFailsWith<StorageException> {
                storage.transaction { state ->
                    state.getJSONObject("accounts").put("a", mapOf("token" to "123&abc"))
                }
            }
            assertEquals("secret_in_state", failure.code)
        }
    }

    @Test
    fun `second instance is rejected while the first holds the lock`() {
        val root = tempRoot()
        val first = AppStorage.open(root.absolutePath)
        try {
            val failure = assertFailsWith<StorageException> { AppStorage.open(root.absolutePath) }
            assertEquals("locked", failure.code)
        } finally {
            first.close()
        }
        // Closing releases both the OS lock and the in-process registry.
        AppStorage.open(root.absolutePath).close()
    }

    @Test
    fun `corrupt state is reported instead of silently reset`() {
        val root = tempRoot()
        File(root, "state.json").writeText("{ not json")
        val failure = assertFailsWith<StorageException> { AppStorage.open(root.absolutePath) }
        assertEquals("corrupt", failure.code)
    }

    @Test
    fun `unfinished commit keeps the backup and asks for recovery`() {
        val root = tempRoot()
        File(root, "state.backup").writeText("{}")
        val failure = assertFailsWith<StorageException> { AppStorage.open(root.absolutePath) }
        assertEquals("recovery_required", failure.code)
    }

    @Test
    fun `new directory starts with the legacy schema`() {
        val root = tempRoot()
        AppStorage.open(root.absolutePath).use { storage ->
            val state = storage.state()
            assertEquals(1, state.getInt("schemaVersion"))
            assertTrue(state.getJSONObject("settings").isEmpty)
            assertEquals(0, state.getJSONArray("downloads").length())
            assertEquals(0, state.getJSONObject("queue").length())
        }
    }
}

private inline fun <T> AppStorage.use(block: (AppStorage) -> T): T =
    try {
        block(this)
    } finally {
        close()
    }
