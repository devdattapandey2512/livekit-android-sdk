/*
 * Copyright 2023-2026 LiveKit, Inc.
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

package io.livekit.android.sample

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Parcelable
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.xwray.groupie.GroupieAdapter
import io.livekit.android.sample.common.R
import io.livekit.android.sample.databinding.CallActivityBinding
import io.livekit.android.sample.dialog.showAudioProcessorSwitchDialog
import io.livekit.android.sample.dialog.showDebugMenuDialog
import io.livekit.android.sample.dialog.showSelectAudioDeviceDialog
import io.livekit.android.sample.model.StressTest
import io.livekit.android.sample.service.ScreenCaptureForegroundService
import io.livekit.android.sample.util.RemoteControlManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

class CallActivity : AppCompatActivity() {

    private val viewModel: CallViewModel by viewModelByFactory {
        val args = intent.getParcelableExtra<BundleArgs>(KEY_ARGS)
            ?: throw NullPointerException("args is null!")

        CallViewModel(
            url = args.url,
            token = args.token,
            e2ee = args.e2eeOn,
            e2eeKey = args.e2eeKey,
            stressTest = args.stressTest,
            application = application,
        )
    }
    private lateinit var binding: CallActivityBinding
    private val screenCaptureIntentLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val resultCode = result.resultCode
            val data = result.data
            if (resultCode != RESULT_OK || data == null) {
                val intent = Intent(this, ScreenCaptureForegroundService::class.java)
                stopService(intent)
                return@registerForActivityResult
            }
            viewModel.startScreenCapture(data)
        }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = CallActivityBinding.inflate(layoutInflater)

        checkAndPromptAccessibilityService()

        setContentView(binding.root)

        // Audience row setup
        val audienceAdapter = GroupieAdapter()
        binding.audienceRow.apply {
            layoutManager = LinearLayoutManager(this@CallActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = audienceAdapter
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.participants
                    .collect { participants ->
                        val items = participants.map { participant -> ParticipantItem(viewModel.room, participant) }
                        audienceAdapter.update(items)
                    }
            }
        }

        // speaker view setup
        val speakerAdapter = GroupieAdapter()
        binding.speakerView.apply {
            layoutManager = LinearLayoutManager(this@CallActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = speakerAdapter
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.primarySpeaker.collectLatest { speaker ->
                    val items = listOfNotNull(speaker)
                        .map { participant -> ParticipantItem(viewModel.room, participant, speakerView = true) }
                    speakerAdapter.update(items)
                }
            }
        }

        // Controls setup
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.cameraEnabled.collect { enabled ->
                    binding.camera.setOnClickListener { viewModel.setCameraEnabled(!enabled) }
                    binding.camera.setImageResource(
                        if (enabled) {
                            R.drawable.outline_videocam_24
                        } else {
                            R.drawable.outline_videocam_off_24
                        },
                    )
                    binding.flipCamera.isEnabled = enabled
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.micEnabled.collect { enabled ->
                    binding.mic.setOnClickListener { viewModel.setMicEnabled(!enabled) }
                    binding.mic.setImageResource(
                        if (enabled) {
                            R.drawable.outline_mic_24
                        } else {
                            R.drawable.outline_mic_off_24
                        },
                    )
                }
            }
        }

        binding.flipCamera.setOnClickListener { viewModel.flipCamera() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.screenshareEnabled.collect { enabled ->
                    binding.screenShare.setOnClickListener {
                        if (enabled) {
                            viewModel.stopScreenCapture()
                            val intent = Intent(this@CallActivity, ScreenCaptureForegroundService::class.java)
                            stopService(intent)
                        } else {
                            requestMediaProjection()
                        }
                    }
                    binding.screenShare.setImageResource(
                        if (enabled) {
                            R.drawable.baseline_cast_connected_24
                        } else {
                            R.drawable.baseline_cast_24
                        },
                    )
                }
            }
        }

        binding.message.setOnClickListener {
            val editText = EditText(this)
            AlertDialog.Builder(this)
                .setTitle("Send Message")
                .setView(editText)
                .setPositiveButton("Send") { dialog, _ ->
                    viewModel.sendData(editText.text?.toString() ?: "")
                }
                .setNegativeButton("Cancel") { _, _ -> }
                .create()
                .show()
        }

        viewModel.enhancedNsEnabled.observe(this) { enabled ->
            binding.enhancedNs.visibility = if (enabled) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }

        binding.enhancedNs.setOnClickListener {
            showAudioProcessorSwitchDialog(viewModel)
        }

        binding.exit.setOnClickListener { finish() }

        // Controls row 2
        binding.audioSelect.setOnClickListener {
            showSelectAudioDeviceDialog(viewModel)
        }
        lifecycleScope.launchWhenCreated {
            viewModel.permissionAllowed.collect { allowed ->
                val resource = if (allowed) R.drawable.account_cancel_outline else R.drawable.account_cancel
                binding.permissions.setImageResource(resource)
            }
        }
        binding.permissions.setOnClickListener {
            viewModel.toggleSubscriptionPermissions()
        }

        binding.debugMenu.setOnClickListener {
            showDebugMenuDialog(viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launchWhenResumed {
            viewModel.error.collect {
                if (it != null) {
                    Toast.makeText(this@CallActivity, "Error: $it", Toast.LENGTH_LONG).show()
                    viewModel.dismissError()
                }
            }
        }

        lifecycleScope.launchWhenResumed {
            viewModel.dataReceived.collect {
                try {
                    // Extract message content assuming "Identity: Message" format
                    val splitIndex = it.indexOf(": ")
                    if (splitIndex != -1) {
                        val messageContent = it.substring(splitIndex + 2)
                        val json = JSONObject(messageContent)
                        if (json.has("action") && json.has("x") && json.has("y")) {
                            val action = json.getString("action")
                            val xPercent = json.getDouble("x").toFloat()
                            val yPercent = json.getDouble("y").toFloat()

                            val metrics = android.util.DisplayMetrics()
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                val windowMetrics = windowManager.currentWindowMetrics
                                val bounds = windowMetrics.bounds
                                metrics.widthPixels = bounds.width()
                                metrics.heightPixels = bounds.height()
                            } else {
                                @Suppress("DEPRECATION")
                                windowManager.defaultDisplay.getRealMetrics(metrics)
                            }
                            val screenWidth = metrics.widthPixels
                            val screenHeight = metrics.heightPixels

                            val x = xPercent * screenWidth
                            val y = yPercent * screenHeight

                            RemoteControlManager.injectTouch(action, x, y)
                            // Don't toast for control messages to avoid spam
                            return@collect
                        }
                    }
                } catch (e: Exception) {
                    // Not a JSON control message, proceed to toast
                }
                Toast.makeText(this@CallActivity, "Data received: $it", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkAndPromptAccessibilityService() {
        val am = getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

        val isServiceEnabled = enabledServices.contains(packageName + "/" + io.livekit.android.sample.service.RemoteControlAccessibilityService::class.java.canonicalName)

        if (!isServiceEnabled) {
            AlertDialog.Builder(this)
                .setTitle("Enable Remote Control")
                .setMessage("To allow remote control, please enable the Accessibility Service for this app in Settings.")
                .setPositiveButton("Settings") { _, _ ->
                    startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun requestMediaProjection() {
        val mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = Intent(this, ScreenCaptureForegroundService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        screenCaptureIntentLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    override fun onDestroy() {
        binding.audienceRow.adapter = null
        binding.speakerView.adapter = null
        super.onDestroy()
    }

    companion object {
        const val KEY_ARGS = "args"
    }

    @Parcelize
    data class BundleArgs(
        val url: String,
        val token: String,
        val e2eeKey: String,
        val e2eeOn: Boolean,
        val stressTest: StressTest,
    ) : Parcelable
}
