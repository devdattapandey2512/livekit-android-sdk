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
import android.content.Context
import android.graphics.Path
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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

    fun injectKeyEvent(keyCode: String) {
        try {
            when (keyCode) {
                // ── Physical buttons (work anytime) ──
                "VOLUME_UP" -> {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    Timber.d("Injected VOLUME_UP")
                }
                "VOLUME_DOWN" -> {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI
                    )
                    Timber.d("Injected VOLUME_DOWN")
                }
                "POWER" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                        Timber.d("Injected POWER (lock screen)")
                    } else {
                        Timber.w("POWER key event requires API 28+")
                    }
                }

                // ── Global navigation actions (work anytime) ──
                "BACK" -> {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    Timber.d("Injected BACK")
                }
                "HOME" -> {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    Timber.d("Injected HOME")
                }
                "RECENT_APPS" -> {
                    performGlobalAction(GLOBAL_ACTION_RECENTS)
                    Timber.d("Injected RECENT_APPS")
                }
                "NOTIFICATIONS" -> {
                    performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                    Timber.d("Injected NOTIFICATIONS")
                }

                // ── Keyboard text events (only when input field is focused) ──
                "BACKSPACE" -> {
                    handleBackspace()
                }
                "ENTER" -> {
                    handleEnter()
                }
                "SPACE" -> {
                    injectTextToFocusedField(" ")
                }
                "TAB" -> {
                    injectTextToFocusedField("\t")
                }
                else -> {
                    // Single character keys: "A"-"Z", "0"-"9", or symbols like ".", ",", "@", etc.
                    if (keyCode.length == 1) {
                        injectTextToFocusedField(keyCode)
                    } else {
                        Timber.w("Unknown key code: $keyCode")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error injecting key event: $keyCode")
        }
    }

    // ── Text injection helpers (require focused editable input field) ──

    /**
     * Finds the currently focused input field.
     * Returns null if no editable input field is focused (i.e., soft keyboard is not active).
     */
    private fun getFocusedInputNode(): AccessibilityNodeInfo? {
        val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode == null) {
            Timber.w("No focused input field — keyboard text input ignored")
            return null
        }
        if (!focusedNode.isEditable) {
            Timber.w("Focused node is not editable — keyboard text input ignored")
            focusedNode.recycle()
            return null
        }
        return focusedNode
    }

    /**
     * Appends text to the currently focused editable input field.
     * Does nothing if no input field is focused.
     */
    private fun injectTextToFocusedField(text: String) {
        val node = getFocusedInputNode() ?: return
        try {
            val currentText = node.text?.toString() ?: ""
            val newText = currentText + text
            val args = Bundle()
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                newText
            )
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Timber.d("Injected text: '$text' → field now: '$newText'")
        } finally {
            node.recycle()
        }
    }

    /**
     * Deletes the last character from the currently focused editable input field.
     * Does nothing if no input field is focused or field is empty.
     */
    private fun handleBackspace() {
        val node = getFocusedInputNode() ?: return
        try {
            val currentText = node.text?.toString() ?: ""
            if (currentText.isEmpty()) {
                Timber.d("BACKSPACE ignored — field is empty")
                return
            }
            val newText = currentText.dropLast(1)
            val args = Bundle()
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                newText
            )
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Timber.d("Injected BACKSPACE → field now: '$newText'")
        } finally {
            node.recycle()
        }
    }

    /**
     * Handles Enter key on the currently focused editable input field.
     * On API 30+, uses ACTION_IME_ENTER. On older APIs, appends a newline.
     */
    private fun handleEnter() {
        val node = getFocusedInputNode() ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                Timber.d("Injected ENTER (ACTION_IME_ENTER)")
            } else {
                // Fallback: append newline
                val currentText = node.text?.toString() ?: ""
                val args = Bundle()
                args.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    currentText + "\n"
                )
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                Timber.d("Injected ENTER (appended newline)")
            }
        } finally {
            node.recycle()
        }
    }
}
