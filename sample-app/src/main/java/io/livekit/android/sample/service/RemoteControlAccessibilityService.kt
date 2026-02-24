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

package io.livekit.android.sample.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import io.livekit.android.sample.util.RemoteControlManager
import timber.log.Timber

class RemoteControlAccessibilityService : AccessibilityService() {

    private var lastStroke: GestureDescription.StrokeDescription? = null
    private var lastX = 0f
    private var lastY = 0f

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.d("RemoteControlAccessibilityService connected")
        RemoteControlManager.registerService(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        RemoteControlManager.unregisterService()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op
    }

    override fun onInterrupt() {
        lastStroke = null
    }

    fun injectTouch(action: String, x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Fallback for older APIs
            if (action == "TAP" || action == "CLICK" || action == "UP") {
                val path = Path()
                path.moveTo(x, y)
                val stroke = GestureDescription.StrokeDescription(path, 0, 50)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                dispatchGesture(gesture, null, null)
            }
            return
        }

        val path = Path()
        try {
            when (action) {
                "DOWN" -> {
                    path.moveTo(x, y)
                    lastStroke = GestureDescription.StrokeDescription(path, 0, 100, true)
                    val gesture = GestureDescription.Builder().addStroke(lastStroke!!).build()
                    dispatchGesture(gesture, null, null)
                    lastX = x
                    lastY = y
                }
                "MOVE" -> {
                    if (lastStroke != null) {
                        path.moveTo(lastX, lastY)
                        path.lineTo(x, y)
                        lastStroke = lastStroke!!.continueStroke(path, 0, 100, true)
                        val gesture = GestureDescription.Builder().addStroke(lastStroke!!).build()
                        dispatchGesture(gesture, null, null)
                        lastX = x
                        lastY = y
                    }
                }
                "UP" -> {
                    if (lastStroke != null) {
                        path.moveTo(lastX, lastY)
                        path.lineTo(x, y)
                        lastStroke = lastStroke!!.continueStroke(path, 0, 100, false)
                        val gesture = GestureDescription.Builder().addStroke(lastStroke!!).build()
                        dispatchGesture(gesture, null, null)
                        lastStroke = null
                    } else {
                        // Single tap fallback
                        path.moveTo(x, y)
                        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
                        val gesture = GestureDescription.Builder().addStroke(stroke).build()
                        dispatchGesture(gesture, null, null)
                    }
                }
                "TAP" -> {
                    path.moveTo(x, y)
                    val stroke = GestureDescription.StrokeDescription(path, 0, 50)
                    val gesture = GestureDescription.Builder().addStroke(stroke).build()
                    dispatchGesture(gesture, null, null)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error injecting touch")
            lastStroke = null
        }
    }
}
