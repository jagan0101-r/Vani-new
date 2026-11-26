package com.example.ai

import android.util.Log
import com.example.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiRepository {

    private val generativeModel by lazy {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
        GenerativeModel(
            modelName = "gemini-3.8-flash",
            apiKey = if (apiKey.isNotBlank()) apiKey else "AIzaSyDummyKeyForBuild"
        )
    }

    suspend fun generateResponse(prompt: String, systemInstruction: String = ""): String = withContext(Dispatchers.IO) {
        try {
            val fullPrompt = if (systemInstruction.isNotBlank()) "$systemInstruction\n\nPrompt: $prompt" else prompt
            val response = generativeModel.generateContent(fullPrompt)
            response.text ?: "Haan jaan, main sun rhi hoon, lekin response nahi mila."
        } catch (e: Exception) {
            Log.e("GeminiRepository", "Error generating content with API key", e)
            "Arey jaan, API key ya network connection mein error aa rhi hai. Phir se bolo na!"
        }
    }
}
