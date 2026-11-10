package teikengstudio.xmpro

import android.app.Application

/**
 * Process-wide holder for long-lived services. Real instances are wired in
 * later milestones; keeping the entry point here avoids a singleton sprawl.
 */
class XmproApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
