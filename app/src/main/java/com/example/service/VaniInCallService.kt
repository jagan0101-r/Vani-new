package com.example.service

import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log

class VaniInCallService : InCallService() {

    companion object {
        var currentCall: Call? = null
            private set
        var instance: VaniInCallService? = null
            private set

        fun answerActiveCall(): Boolean {
            return try {
                val call = currentCall
                if (call != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        call.answer(VideoProfile.STATE_AUDIO_ONLY)
                    } else {
                        call.answer(0)
                    }
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e("VaniInCallService", "Failed to answer call", e)
                false
            }
        }

        fun rejectActiveCall(): Boolean {
            return try {
                val call = currentCall
                if (call != null) {
                    call.reject(false, null)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e("VaniInCallService", "Failed to reject call", e)
                false
            }
        }
    }

    override fun onCallAdded(call: Call?) {
        super.onCallAdded(call)
        currentCall = call
        instance = this
        Log.d("VaniInCallService", "Call added state: ${call?.state}")
    }

    override fun onCallRemoved(call: Call?) {
        super.onCallRemoved(call)
        if (currentCall == call) {
            currentCall = null
        }
        Log.d("VaniInCallService", "Call removed")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }
}
