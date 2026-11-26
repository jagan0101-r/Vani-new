package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class VoiceManager(
    private val context: Context,
    private val onSpeechResult: (String) -> Unit,
    private val onWakeWordDetected: () -> Unit,
    private val onAmplitudeChanged: (Float) -> Unit
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false
    private var isTtsReady = false
    private val handler = Handler(Looper.getMainLooper())

    init {
        initTts()
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = Locale("en", "IN")
                val result = textToSpeech?.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech?.language = Locale("hi", "IN")
                }
                textToSpeech?.setSpeechRate(1.0f)
                textToSpeech?.setPitch(1.1f)
                isTtsReady = true
                Log.d("VoiceManager", "TTS initialized with Indian-accent female voice profile")
            }
        }
    }

    fun speak(text: String) {
        if (isTtsReady) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "VANI_TTS")
        }
    }

    fun stopSpeaking() {
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error stopping TTS", e)
        }
    }

    fun startListening() {
        if (isListening) return
        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(this@VoiceManager)
                }
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            }
            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            isListening = false
            Log.e("VoiceManager", "Error starting SpeechRecognizer", e)
            restartListeningAfterDelay()
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
            isListening = false
            onAmplitudeChanged(0.0f)
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error stopping SpeechRecognizer", e)
        }
    }

    private fun restartListeningAfterDelay() {
        handler.postDelayed({
            startListening()
        }, 800)
    }

    override fun onReadyForSpeech(params: Bundle?) {
        isListening = true
    }

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {
        val amplitude = ((rmsdB + 2f) / 12f).coerceIn(0.0f, 1.0f)
        onAmplitudeChanged(amplitude)
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        isListening = false
        onAmplitudeChanged(0.0f)
    }

    override fun onError(error: Int) {
        isListening = false
        onAmplitudeChanged(0.0f)
        restartListeningAfterDelay()
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        onAmplitudeChanged(0.0f)
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val spokenText = matches[0]
            processSpokenText(spokenText)
        }
        restartListeningAfterDelay()
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val partialText = matches[0]
            if (partialText.contains("hey vani", ignoreCase = true) ||
                partialText.contains("vani", ignoreCase = true) ||
                partialText.contains("wake up", ignoreCase = true) ||
                partialText.contains("stop", ignoreCase = true) ||
                partialText.contains("chup", ignoreCase = true)) {
                processSpokenText(partialText)
            }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun processSpokenText(text: String) {
        val lower = text.lowercase()
        if (lower.contains("stop") || lower.contains("pause") || lower.contains("chup") || lower.contains("shut up") || lower.contains("shant")) {
            // Trigger stop speaking
            onSpeechResult(text)
        } else if (lower.contains("hey vani") || lower.contains("hey wake up") || lower.contains("wake up") || lower == "vani") {
            onWakeWordDetected()
        } else {
            onSpeechResult(text)
        }
    }

    fun shutdown() {
        stopListening()
        stopSpeaking()
        textToSpeech?.shutdown()
    }
}
