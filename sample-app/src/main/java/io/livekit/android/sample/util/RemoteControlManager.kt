/*
 * Copyright 2026 LiveKit, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    fun injectKeyEvent(keyCode: String) {
        val service = serviceRef?.get()
        if (service != null) {
            service.injectKeyEvent(keyCode)
        } else {
            Timber.w("RemoteControlManager: Service not connected")
        }
    }
}
