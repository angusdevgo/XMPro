package teikengstudio.xmpro

import android.app.Application
import teikengstudio.xmpro.data.AppContainer

/** Owns the single service graph for the process. */
class XmproApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // A crash during a debit leaves `submitting` receipts; reconcile them
        // locally before any playback or download can start.
        runCatching { container.ledger.recoverAll() }
    }
}
