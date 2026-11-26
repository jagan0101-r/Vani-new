package com.example.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.telecom.TelecomManager
import android.util.Log
import com.example.service.VaniInCallService

class DeviceController(private val context: Context) {

    private val cameraManager by lazy { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val telecomManager by lazy { context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager }

    private var torchState = false

    fun toggleTorch(enable: Boolean? = null): Boolean {
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return false
            val newState = enable ?: !torchState
            cameraManager.setTorchMode(cameraId, newState)
            torchState = newState
            return true
        } catch (e: Exception) {
            Log.e("DeviceController", "Torch error", e)
            return false
        }
    }

    fun adjustVolume(increase: Boolean) {
        val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    fun setVolume(levelPercent: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (max * levelPercent / 100).coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
    }

    fun getBatteryLevel(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    fun openApp(appName: String): Boolean {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (pkg in packages) {
            val label = pm.getApplicationLabel(pkg).toString()
            if (label.contains(appName, ignoreCase = true)) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
            }
        }
        return false
    }

    fun makeCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("DeviceController", "Call error", e)
        }
    }

    fun answerCall(): Boolean {
        // First try VaniInCallService active call
        if (VaniInCallService.answerActiveCall()) {
            return true
        }
        try {
            if (telecomManager != null) {
                telecomManager.acceptRingingCall()
                return true
            }
        } catch (e: Exception) {
            Log.e("DeviceController", "Answer call error", e)
        }
        return false
    }

    fun endCall(): Boolean {
        // First try VaniInCallService active call
        if (VaniInCallService.rejectActiveCall()) {
            return true
        }
        try {
            if (telecomManager != null) {
                return telecomManager.endCall()
            }
        } catch (e: Exception) {
            Log.e("DeviceController", "End call error", e)
        }
        return false
    }

    fun sendWhatsAppMessage(phoneNumber: String, message: String) {
        try {
            val url = "https://api.whatsapp.com/send?phone=$phoneNumber&text=${Uri.encode(message)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("DeviceController", "WhatsApp error", e)
        }
    }

    fun openGoogleMaps(query: String) {
        try {
            val uri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${Uri.encode(query)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
