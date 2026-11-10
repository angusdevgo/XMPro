package teikengstudio.xmpro.data

import java.time.Instant
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import teikengstudio.xmpro.model.MAX_USAGE_SECONDS
import teikengstudio.xmpro.model.Quota
import teikengstudio.xmpro.model.ResolvedAudio
import teikengstudio.xmpro.model.XmlyException

enum class DebitState { prepared, submitting, confirmed, uncertain, rejected }

data class DownloadReceipt(
    val operationId: String,
    val accountIdentity: String,
    val trackId: String,
    val taskId: String,
    val seconds: Int,
    val balanceBefore: Int,
    val balanceAfter: Int?,
    val createdAt: Instant,
    val state: DebitState,
    val historical: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("operationId", operationId)
        put("accountIdentity", accountIdentity)
        put("trackId", trackId)
        put("taskId", taskId)
        put("seconds", seconds)
        put("balanceBefore", balanceBefore)
        put("balanceAfter", balanceAfter ?: JSONObject.NULL)
        put("createdAt", createdAt.toEpochMilli())
        put("state", state.name)
        put("historical", historical)
    }

    companion object {
        fun fromJson(value: JSONObject): DownloadReceipt {
            val historical = value.optBoolean("historical", false) || value.optString("state") == "historical"
            val state = if (value.optString("state") == "historical") {
                DebitState.confirmed
            } else {
                DebitState.entries.firstOrNull { it.name == value.optString("state") }
                    ?: throw XmlyException("invalid_ledger", "扣时记录损坏，请保留资料并恢复备份")
            }
            val seconds = value.optInt("seconds", -1)
            val balanceBefore = value.optInt("balanceBefore", -1)
            val createdAt = value.optLong("createdAt", -1)
            if (seconds !in 1..MAX_USAGE_SECONDS ||
                balanceBefore < 0 ||
                createdAt < 0 ||
                value.optString("operationId").isEmpty() ||
                value.optString("accountIdentity").isEmpty() ||
                value.optString("trackId").isEmpty() ||
                value.optString("taskId").isEmpty()
            ) {
                throw XmlyException("invalid_ledger", "扣时记录损坏，请保留资料并恢复备份")
            }
            val balanceAfter = if (value.isNull("balanceAfter")) null else value.optInt("balanceAfter", -1)
            if (balanceAfter != null && balanceAfter < 0) {
                throw XmlyException("invalid_ledger", "扣时记录损坏，请保留资料并恢复备份")
            }
            return DownloadReceipt(
                operationId = value.getString("operationId"),
                accountIdentity = value.getString("accountIdentity"),
                trackId = value.getString("trackId"),
                taskId = value.getString("taskId"),
                seconds = seconds,
                balanceBefore = balanceBefore,
                balanceAfter = balanceAfter,
                createdAt = Instant.ofEpochMilli(createdAt),
                state = state,
                historical = historical,
            )
        }
    }
}

/** Account-scoped listening balance operations; the client implements this. */
interface QuotaSource {
    fun quota(identity: String): Quota
    fun debit(identity: String, seconds: Int, operationId: String): Quota
}

/**
 * Durable listening-time bookkeeping.
 *
 * A receipt moves prepared → submitting → confirmed, or stays `uncertain` when
 * the network result is unknown. Startup recovery downgrades `submitting` to
 * `uncertain` so a crash can never silently double-charge.
 */
class UsageLedger(
    private val storage: AppStorage,
    private val quotas: QuotaSource,
    private val clock: () -> Instant = { Instant.now() },
) {
    fun receipts(identity: String): List<DownloadReceipt> {
        val items = ledger(identity).optJSONArray("receipts") ?: JSONArray()
        return (0 until items.length()).map { index ->
            val receipt = DownloadReceipt.fromJson(items.getJSONObject(index))
            if (receipt.accountIdentity != identity) {
                throw XmlyException("invalid_ledger", "扣时记录与账号不符")
            }
            receipt
        }
    }

    fun isBlocked(identity: String): Boolean {
        val ledger = ledger(identity)
        return ledger.optBoolean("usage_uncertain", false) ||
            !ledger.isNull("usage_pending") ||
            receipts(identity).any { it.state == DebitState.submitting || it.state == DebitState.uncertain }
    }

    fun debt(identity: String): Double = ledger(identity).optDouble("usage_seconds", 0.0)

    /** Local recovery only: never replays a network request on startup. */
    fun recoverAll() {
        val accounts = storage.state().optJSONObject("accounts") ?: return
        for (identity in accounts.keys().asSequence().toList()) {
            recover(identity)
        }
    }

    fun recover(identity: String) {
        val ledger = ledger(identity)
        val entries = receipts(identity)
        val pending = ledger.optJSONObject("usage_pending")
        val recoverPending = pending?.optString("state") == "submitting"
        if (!recoverPending && entries.none { it.state == DebitState.submitting }) return
        write(identity) { next ->
            val items = next.optJSONArray("receipts")
            if (items != null) {
                for (index in 0 until items.length()) {
                    val entry = items.optJSONObject(index) ?: continue
                    if (entry.optString("state") == "submitting") entry.put("state", "uncertain")
                }
            }
            if (recoverPending) {
                next.getJSONObject("usage_pending").put("state", "uncertain")
                next.put("usage_uncertain", true)
            }
        }
    }

    /** Records a pending receipt, then submits it and stores the final state. */
    fun prepay(
        identity: String,
        taskId: String,
        audio: ResolvedAudio,
        userConfirmed: Boolean,
        expectedSeconds: Int? = null,
        expectedBalance: Int? = null,
    ): DownloadReceipt? {
        if (!audio.requiresReward) return null
        if (audio.accountIdentity != identity || taskId.isBlank()) {
            throw XmlyException("account_mismatch", "声音授权与下载账号不符")
        }
        recover(identity)
        guard(identity)
        for (receipt in receipts(identity).reversed()) {
            if (receipt.taskId != taskId) continue
            if (receipt.trackId != audio.track.id) {
                throw XmlyException("task_mismatch", "原任务与声音不符")
            }
            if (receipt.state == DebitState.confirmed && !receipt.historical) return receipt
        }
        val duration = audio.track.duration
        if (!duration.isFinite() || duration <= 0 || duration > MAX_USAGE_SECONDS) {
            throw XmlyException("invalid_duration", "缺少有效的整首时长，不能预扣下载")
        }
        val seconds = kotlin.math.ceil(duration).toInt()
        if (!userConfirmed) {
            throw XmlyException("confirmation_required", "请先确认本首扣时及不可自动退款说明")
        }
        settle(identity)
        val quota = quotas.quota(identity)
        if ((expectedSeconds != null && expectedSeconds != seconds) ||
            (expectedBalance != null && expectedBalance != quota.remainingSeconds)
        ) {
            throw XmlyException("confirmation_changed", "余额或听时已变化，请重新确认")
        }
        if (quota.remainingSeconds < seconds) {
            throw XmlyException("insufficient_quota", "听时不足，请前往官方 App 获取听时", definitive = true)
        }
        val operationId = UUID.randomUUID().toString()
        val receipt = DownloadReceipt(
            operationId = operationId,
            accountIdentity = identity,
            trackId = audio.track.id,
            taskId = taskId,
            seconds = seconds,
            balanceBefore = quota.remainingSeconds,
            balanceAfter = null,
            createdAt = clock(),
            state = DebitState.submitting,
        )
        write(identity) { next ->
            next.getJSONArray("receipts").put(receipt.toJson())
            next.put("usage_pending", receipt.toJson())
        }
        return submit(receipt)
    }

    /**
     * Sends the accumulated local debt in protocol-sized chunks.
     *
     * Listening debt is journalled as `usage_pending` (like the previous
     * implementation) instead of a download receipt, because it has no task or
     * track to attach to.
     */
    fun settle(identity: String): Int {
        recover(identity)
        var sent = 0
        while (true) {
            val remaining = debt(identity).toInt()
            if (remaining <= 0) return sent
            guard(identity)
            val amount = if (remaining > MAX_USAGE_SECONDS) MAX_USAGE_SECONDS else remaining
            val quota = quotas.quota(identity)
            if (quota.remainingSeconds < amount) {
                throw XmlyException("insufficient_quota", "听时不足，请前往官方 App 获取听时", definitive = true)
            }
            val operationId = UUID.randomUUID().toString()
            val pending = JSONObject().apply {
                put("operationId", operationId)
                put("seconds", amount)
                put("balanceBefore", quota.remainingSeconds)
                put("createdAt", clock().toEpochMilli())
                put("state", "submitting")
            }
            write(identity) { next -> next.put("usage_pending", pending) }
            val state = try {
                quotas.debit(identity, amount, operationId)
                "confirmed"
            } catch (error: XmlyException) {
                if (error.code == "rejected" && error.definitive) "rejected" else "uncertain"
            } catch (_: Exception) {
                "uncertain"
            }
            write(identity) { next ->
                next.remove("usage_pending")
                next.put("usage_uncertain", state == "uncertain")
                if (state == "confirmed") {
                    val left = next.optDouble("usage_seconds", 0.0) - amount
                    next.put("usage_seconds", if (left > 0) left else 0.0)
                }
            }
            if (state == "uncertain") {
                throw XmlyException(
                    "usage_uncertain",
                    "上次扣时结果未知，请先到官方 App 核对并确认是否已扣",
                )
            }
            if (state == "rejected") {
                throw XmlyException("rejected", "喜马拉雅拒绝了扣时请求", definitive = true)
            }
            sent += amount
        }
    }

    /** Adds sampled listening seconds; a failed write stops the watch upstream. */
    fun addDebt(identity: String, seconds: Double) {
        if (!seconds.isFinite() || seconds <= 0) return
        write(identity) { next ->
            next.put("usage_seconds", next.optDouble("usage_seconds", 0.0) + seconds)
        }
    }

    private fun submit(receipt: DownloadReceipt): DownloadReceipt {
        val updated = try {
            val quota = quotas.debit(receipt.accountIdentity, receipt.seconds, receipt.operationId)
            receipt.copy(state = DebitState.confirmed, balanceAfter = quota.remainingSeconds)
        } catch (error: XmlyException) {
            if (error.code == "rejected" && error.definitive) {
                receipt.copy(state = DebitState.rejected)
            } else {
                receipt.copy(state = DebitState.uncertain)
            }
        } catch (_: Exception) {
            receipt.copy(state = DebitState.uncertain)
        }
        write(receipt.accountIdentity) { next ->
            val items = next.getJSONArray("receipts")
            for (index in 0 until items.length()) {
                val entry = items.optJSONObject(index) ?: continue
                if (entry.optString("operationId") == receipt.operationId) {
                    items.put(index, updated.toJson())
                }
            }
            next.remove("usage_pending")
            next.put("usage_uncertain", updated.state == DebitState.uncertain)
            if (updated.state == DebitState.confirmed) {
                val debt = next.optDouble("usage_seconds", 0.0) - updated.seconds
                next.put("usage_seconds", if (debt > 0) debt else 0.0)
            }
        }
        if (updated.state == DebitState.uncertain) {
            throw XmlyException(
                "usage_uncertain",
                "上次扣时结果未知，请先到官方 App 核对并确认是否已扣",
            )
        }
        if (updated.state == DebitState.rejected) {
            throw XmlyException("rejected", "喜马拉雅拒绝了扣时请求", definitive = true)
        }
        return updated
    }

    private fun guard(identity: String) {
        if (isBlocked(identity)) {
            throw XmlyException("usage_uncertain", "上次扣时结果未知，请先到官方 App 核对并确认是否已扣")
        }
    }

    private fun ledger(identity: String): JSONObject {
        val accounts = storage.state().optJSONObject("accounts")
            ?: throw XmlyException("missing_account", "请先登录原账号")
        val account = accounts.optJSONObject(identity)
            ?: throw XmlyException("missing_account", "请先登录原账号")
        if (!account.isNull("ledger") && account.opt("ledger") !is JSONObject) {
            throw XmlyException("invalid_ledger", "听时记录损坏，请保留资料")
        }
        return account.optJSONObject("ledger") ?: JSONObject()
    }

    private fun write(identity: String, update: (JSONObject) -> Unit) {
        storage.transaction { state ->
            val accounts = state.optJSONObject("accounts")
                ?: throw XmlyException("missing_account", "请先登录原账号")
            val account = accounts.optJSONObject(identity)
                ?: throw XmlyException("missing_account", "请先登录原账号")
            val existing = account.opt("ledger")
            if (existing != null && existing !is JSONObject) {
                throw XmlyException("invalid_ledger", "听时记录损坏，请保留资料")
            }
            val ledger = account.optJSONObject("ledger") ?: JSONObject().also {
                it.put("receipts", JSONArray())
                it.put("usage_seconds", 0.0)
                account.put("ledger", it)
            }
            if (!ledger.has("receipts")) ledger.put("receipts", JSONArray())
            update(ledger)
            // Validate the receipt shapes before the transaction commits.
            receiptsOf(ledger)
        }
    }

    private fun receiptsOf(ledger: JSONObject): List<DownloadReceipt> {
        val items = ledger.optJSONArray("receipts") ?: return emptyList()
        return (0 until items.length()).map { index -> DownloadReceipt.fromJson(items.getJSONObject(index)) }
    }
}
