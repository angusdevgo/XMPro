package teikengstudio.xmpro.data

import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import teikengstudio.xmpro.model.Quota
import teikengstudio.xmpro.model.ResolvedAudio
import teikengstudio.xmpro.model.Track
import teikengstudio.xmpro.model.XmlyException

private class FakeQuota(var remaining: Int = 3600) : QuotaSource {
    var failNext: Exception? = null
    val debits = mutableListOf<Pair<Int, String>>()

    override fun quota(identity: String): Quota = Quota(remaining, newDevice = false, checkedAt = Instant.EPOCH)

    override fun debit(identity: String, seconds: Int, operationId: String): Quota {
        failNext?.let {
            failNext = null
            throw it
        }
        debits += seconds to operationId
        remaining -= seconds
        return Quota(remaining, newDevice = false, checkedAt = Instant.EPOCH)
    }
}

class UsageLedgerTest {
    private val roots = mutableListOf<File>()
    private val identity = "account-1"

    private fun storage(): AppStorage {
        val root = Files.createTempDirectory("xmpro-ledger-").toFile().also { roots += it }
        return AppStorage.open(root.absolutePath).apply {
            transaction { state ->
                state.getJSONObject("accounts").put(identity, org.json.JSONObject())
            }
        }
    }

    private fun audio(requiresReward: Boolean = true, duration: Double = 300.0) = ResolvedAudio(
        url = "https://example.com/a.m4a",
        requiresReward = requiresReward,
        track = Track("123456", title = "第一集", duration = duration),
        actualQuality = "m4a_64",
        accountIdentity = identity,
    )

    @AfterTest
    fun cleanup() {
        roots.forEach { it.deleteRecursively() }
        roots.clear()
    }

    @Test
    fun `prepay records a confirmed receipt and subtracts the fee`() {
        val store = storage()
        val quota = FakeQuota()
        val ledger = UsageLedger(store, quota, clock = { Instant.parse("2026-11-10T00:00:00Z") })
        val receipt = ledger.prepay(identity, "task-1", audio(), userConfirmed = true)
        assertNotNull(receipt)
        assertEquals(DebitState.confirmed, receipt.state)
        assertEquals(300, receipt.seconds)
        assertEquals(3300, receipt.balanceAfter)
        assertEquals(1, ledger.receipts(identity).size)
        assertEquals(emptyList(), ledger.receipts(identity).filter { it.state == DebitState.submitting })
        store.close()
    }

    @Test
    fun `prepay is idempotent for the same task`() {
        val store = storage()
        val quota = FakeQuota()
        val ledger = UsageLedger(store, quota)
        val first = ledger.prepay(identity, "task-1", audio(), userConfirmed = true)!!
        val second = ledger.prepay(identity, "task-1", audio(), userConfirmed = true)!!
        assertEquals(first.operationId, second.operationId)
        assertEquals(1, quota.debits.size)
        store.close()
    }

    @Test
    fun `free audio never charges and needs no confirmation`() {
        val store = storage()
        val ledger = UsageLedger(store, FakeQuota())
        assertNull(ledger.prepay(identity, "task-2", audio(requiresReward = false), userConfirmed = false))
        store.close()
    }

    @Test
    fun `unconfirmed prepay is rejected before any debit`() {
        val store = storage()
        val quota = FakeQuota()
        val ledger = UsageLedger(store, quota)
        val failure = assertFailsWith<XmlyException> {
            ledger.prepay(identity, "task-1", audio(), userConfirmed = false)
        }
        assertEquals("confirmation_required", failure.code)
        assertTrue(quota.debits.isEmpty())
        store.close()
    }

    @Test
    fun `unknown debit result blocks the account until reconciled`() {
        val store = storage()
        val quota = FakeQuota().apply { failNext = java.io.IOException("timeout") }
        val ledger = UsageLedger(store, quota)
        assertEquals(
            "usage_uncertain",
            assertFailsWith<XmlyException> { ledger.prepay(identity, "task-1", audio(), userConfirmed = true) }.code,
        )
        assertTrue(ledger.isBlocked(identity))
        assertEquals(DebitState.uncertain, ledger.receipts(identity).single().state)
        // A blocked account cannot start another billed action.
        assertEquals(
            "usage_uncertain",
            assertFailsWith<XmlyException> { ledger.prepay(identity, "task-2", audio(), userConfirmed = true) }.code,
        )
        store.close()
    }

    @Test
    fun `startup recovery downgrades submitting receipts`() {
        val store = storage()
        store.transaction { state ->
            val account = state.getJSONObject("accounts").getJSONObject(identity)
            val ledger = org.json.JSONObject()
            ledger.put("receipts", org.json.JSONArray().apply {
                put(DownloadReceipt(
                    operationId = "op-1",
                    accountIdentity = identity,
                    trackId = "xmly:1",
                    taskId = "task-1",
                    seconds = 60,
                    balanceBefore = 100,
                    balanceAfter = null,
                    createdAt = Instant.EPOCH,
                    state = DebitState.submitting,
                ).toJson())
            })
            ledger.put("usage_seconds", 0.0)
            account.put("ledger", ledger)
        }
        val ledger = UsageLedger(store, FakeQuota())
        ledger.recoverAll()
        assertEquals(DebitState.uncertain, ledger.receipts(identity).single().state)
        assertTrue(ledger.isBlocked(identity))
        store.close()
    }

    @Test
    fun `settle sends accumulated listening debt in one chunk`() {
        val store = storage()
        val quota = FakeQuota(remaining = 1000)
        val ledger = UsageLedger(store, quota)
        ledger.addDebt(identity, 180.5)
        assertEquals(180, ledger.settle(identity))
        assertEquals(180, quota.debits.single().first)
        assertEquals(820, quota.remaining)
        // Sub-second leftovers stay local until they reach a whole second.
        assertEquals(0.5, ledger.debt(identity), 0.001)
        assertEquals(0, ledger.receipts(identity).size)
        assertTrue(!ledger.isBlocked(identity))
        store.close()
    }

    @Test
    fun `definitive rejection is not treated as uncertain`() {
        val store = storage()
        val quota = FakeQuota().apply {
            failNext = XmlyException("rejected", "喜马拉雅拒绝了请求", definitive = true)
        }
        val ledger = UsageLedger(store, quota)
        assertEquals(
            "rejected",
            assertFailsWith<XmlyException> { ledger.prepay(identity, "task-1", audio(), userConfirmed = true) }.code,
        )
        assertEquals(DebitState.rejected, ledger.receipts(identity).single().state)
        assertTrue(!ledger.isBlocked(identity))
        store.close()
    }

    @Test
    fun `insufficient quota is checked before submission`() {
        val store = storage()
        val quota = FakeQuota(remaining = 10)
        val ledger = UsageLedger(store, quota)
        val failure = assertFailsWith<XmlyException> {
            ledger.prepay(identity, "task-1", audio(), userConfirmed = true)
        }
        assertEquals("insufficient_quota", failure.code)
        assertTrue(quota.debits.isEmpty())
        store.close()
    }
}
