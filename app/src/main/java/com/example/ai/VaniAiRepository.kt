package com.example.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VaniAiRepository {

    suspend fun getAssistantResponse(userPrompt: String, contextInfo: String = ""): String = withContext(Dispatchers.IO) {
        try {
            val model = Firebase.ai.generativeModel("gemini-3.8-flash")
            val systemInstruction = "You are VANI (Virtual Assistant for Native Intelligence), an advanced Android AI assistant created by Mr. Rinku with Google AI. " +
                    "Your personality is friendly, affectionate, and helpful (refer to user warmly as 'jaan' or dear when appropriate, in Hindi/Hinglish and English). " +
                    "Current device context: $contextInfo. " +
                    "Respond concisely and naturally in Hinglish or English as appropriate."

            val prompt = "$systemInstruction\n\nUser command: $userPrompt"
            val response = model.generateContent(prompt)
            response.text ?: "Haan jaan, main sun rhi hoon, lekin samajh nahi paayi."
        } catch (e: Exception) {
            Log.e("VaniAi", "Gemini AI generation failed", e)
            "Arey jaan, network connection ya AI mein thodi problem aa rhi hai. Phir se bolo na!"
        }
    }
}
