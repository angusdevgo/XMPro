package teikengstudio.xmpro.data

import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import teikengstudio.xmpro.model.XmlyException

class StorageException(val code: String, override val message: String) : Exception(message)

/**
 * Single-writer JSON state store.
 *
 * The on-disk contract matches the previous implementation: `state.json` plus
 * an exclusive `state.lock`, written through a flushed temporary file and an
 * atomic rename so a crash never leaves a half-written library.
 */
class AppStorage private constructor(
    val directory: File,
    private val channel: FileChannel,
    private val fileLock: FileLock,
    initialState: JSONObject,
) {
    @Volatile
    private var current: JSONObject = initialState

    @Volatile
    private var closed = false

    private val writeLock = Any()

    /** Defensive copy; callers never share the live tree. */
    fun state(): JSONObject = synchronized(writeLock) { JSONObject(current.toString()) }

    fun rawState(): JSONObject = current

    fun transaction(update: (JSONObject) -> Unit) {
        synchronized(writeLock) {
            if (closed) throw StorageException("closed", "资料目录已关闭")
            val next = JSONObject(current.toString())
            update(next)
            validate(next)
            val bytes = next.toString().toByteArray()
            if (bytes.size > MAX_STATE_BYTES) {
                throw StorageException("too_large", "资料超过大小限制，未保存更改")
            }
            val temporary = File(directory, "state.${UUID.randomUUID()}.tmp")
            try {
                FileOutputStream(temporary).use { output ->
                    output.write(bytes)
                    output.flush()
                    output.fd.sync()
                }
                Files.move(
                    temporary.toPath(),
                    File(directory, "state.json").toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
                current = next
            } catch (error: StorageException) {
                throw error
            } catch (error: Exception) {
                throw StorageException("write_failed", "资料保存失败，原有资料未被替换")
            } finally {
                if (temporary.exists() && !temporary.delete()) {
                    // An uncommitted temporary file is safe to leave behind.
                }
            }
        }
    }

    fun close() {
        synchronized(writeLock) {
            if (closed) return
            closed = true
            runCatching { fileLock.release() }
            runCatching { channel.close() }
            synchronized(OPEN_DIRECTORIES) { OPEN_DIRECTORIES.remove(directory.absolutePath) }
        }
    }

    companion object {
        const val MAX_STATE_BYTES = 64 * 1024 * 1024
        private val OPEN_DIRECTORIES = mutableSetOf<String>()
        private val SECRET_KEYS = setOf(
            "token",
            "device_id",
            "deviceId",
            "cookie",
            "cookie2",
            "x-tk",
            "headers",
            "password",
            "verificationCode",
        )

        fun defaultState(): JSONObject = JSONObject().apply {
            put("schemaVersion", 1)
            put("activeAccountId", JSONObject.NULL)
            put("primaryAccountId", JSONObject.NULL)
            put("settings", JSONObject())
            put("accounts", JSONObject())
            put("downloads", JSONArray())
            put("library", JSONObject())
            put("officialProgress", JSONObject())
            put("queue", JSONObject())
        }

        /**
         * @throws StorageException when the directory is shared, the state file
         * is corrupt, or a previous write never completed.
         */
        fun open(absoluteDirectory: String): AppStorage {
            val folder = File(absoluteDirectory)
            if (!folder.isAbsolute) throw StorageException("directory", "请选择绝对路径的资料目录")
            folder.mkdirs()
            val canonical = folder.canonicalFile
            val key = canonical.absolutePath
            synchronized(OPEN_DIRECTORIES) {
                if (!OPEN_DIRECTORIES.add(key)) throw StorageException("locked", "数据目录已被另一实例使用")
            }
            var channel: FileChannel? = null
            try {
                channel = FileChannel.open(
                    File(canonical, "state.lock").toPath(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                )
                val lock = try {
                    channel.tryLock()
                } catch (_: OverlappingFileLockException) {
                    null
                } ?: throw StorageException("locked", "数据目录已被另一实例使用")

                val stateFile = File(canonical, "state.json")
                val state = when {
                    stateFile.isFile -> readState(stateFile)
                    File(canonical, "state.backup").isFile -> throw StorageException(
                        "recovery_required",
                        "资料提交未完成，请保留资料目录并恢复备份",
                    )
                    else -> defaultState()
                }
                sweepSessionDirectories(canonical)
                return AppStorage(canonical, channel, lock, state)
            } catch (error: Exception) {
                runCatching { channel?.close() }
                synchronized(OPEN_DIRECTORIES) { OPEN_DIRECTORIES.remove(key) }
                throw error
            }
        }

        private fun readState(file: File): JSONObject {
            try {
                if (file.length() > MAX_STATE_BYTES) throw IllegalArgumentException("too large")
                val parsed = JSONObject(file.readText())
                validate(parsed)
                return parsed
            } catch (_: Exception) {
                throw StorageException("corrupt", "资料文件损坏，请恢复备份或选择其他资料目录")
            }
        }

        private fun sweepSessionDirectories(directory: File) {
            for (name in listOf("captcha", "playback-cache")) {
                val target = File(directory, name)
                if (target.exists()) target.deleteRecursively()
            }
        }

        fun validate(value: JSONObject) {
            val active = value.opt("activeAccountId")
            val primary = value.opt("primaryAccountId")
            val invalid = value.optInt("schemaVersion", -1) != 1 ||
                value.opt("settings") !is JSONObject ||
                value.opt("accounts") !is JSONObject ||
                value.opt("downloads") !is JSONArray ||
                value.opt("library") !is JSONObject ||
                value.opt("queue") !is JSONObject ||
                (value.has("history") && value.opt("history") !is JSONObject) ||
                (value.has("officialProgress") && value.opt("officialProgress") !is JSONObject) ||
                (active != JSONObject.NULL && active !is String) ||
                (primary != JSONObject.NULL && primary !is String)
            if (invalid) throw StorageException("corrupt", "资料格式无效，未保存更改")
            inspect(value)
        }

        private fun inspect(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val name = keys.next()
                        if (SECRET_KEYS.contains(name)) {
                            throw StorageException("secret_in_state", "登录秘密不能写入资料文件")
                        }
                        inspect(node.opt(name))
                    }
                }
                is JSONArray -> for (index in 0 until node.length()) inspect(node.opt(index))
                is Double -> if (!node.isFinite()) throw StorageException("corrupt", "资料包含无效数字")
                is Float -> if (!node.isFinite()) throw StorageException("corrupt", "资料包含无效数字")
            }
        }
    }
}
