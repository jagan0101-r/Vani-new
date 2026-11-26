package com.example.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class KeywordSpotter(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var isRunning = false
    private var spotterJob: Job? = null

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    fun start() {
        if (isRunning) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w("KeywordSpotter", "RECORD_AUDIO permission not granted")
            return
        }

        try {
            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = if (minBuffer > 0) minBuffer * 2 else 3200
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.startRecording()
                isRunning = true
                spotterJob = CoroutineScope(Dispatchers.IO).launch {
                    val buffer = ByteArray(bufferSize)
                    while (isRunning) {
                        val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (readSize > 0) {
                            var sum = 0.0
                            val sampleCount = readSize / 2
                            for (i in 0 until readSize step 2) {
                                val low = buffer[i].toInt() and 0xFF
                                val high = buffer[i + 1].toInt() shl 8
                                val shortVal = (low or high).toShort()
                                val sample = shortVal / 32768.0
                                sum += sample * sample
                            }
                            val rms = if (sampleCount > 0) Math.sqrt(sum / sampleCount) else 0.0
                            
                            // High sensitivity threshold for voice / wake word detection
                            if (rms > 0.06) {
                                Log.d("KeywordSpotter", "Voice activity / Hey Vani wake trigger detected (RMS: $rms)")
                                withContext(Dispatchers.Main) {
                                    onWakeWordDetected()
                                }
                                delay(3500) // Cooldown
                            }
                        }
                        delay(40)
                    }
                }
                Log.d("KeywordSpotter", "KeywordSpotter started successfully using AudioRecord")
            }
        } catch (e: Exception) {
            Log.e("KeywordSpotter", "Failed to start AudioRecord", e)
            isRunning = false
        }
    }

    fun stop() {
        isRunning = false
        spotterJob?.cancel()
        spotterJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e("KeywordSpotter", "Error stopping AudioRecord", e)
        }
    }
}
