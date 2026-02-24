package io.livekit.android.sample.util

import io.livekit.android.sample.service.RemoteControlAccessibilityService
import timber.log.Timber
import java.lang.ref.WeakReference

object RemoteControlManager {
    private var serviceRef: WeakReference<RemoteControlAccessibilityService>? = null

    fun registerService(service: RemoteControlAccessibilityService) {
        serviceRef = WeakReference(service)
        Timber.d("RemoteControlManager: Service registered")
    }

    fun unregisterService() {
        serviceRef = null
        Timber.d("RemoteControlManager: Service unregistered")
    }

    fun injectTouch(action: String, x: Float, y: Float) {
        val service = serviceRef?.get()
        if (service != null) {
            service.injectTouch(action, x, y)
        } else {
            Timber.w("RemoteControlManager: Service not connected")
        }
    }
}
