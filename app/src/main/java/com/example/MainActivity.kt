package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.accessibility.VaniAccessibilityService
import com.example.ai.GeminiRepository
import com.example.data.MessageRepository
import com.example.data.VaniDatabase
import com.example.device.DeviceController
import com.example.service.VaniForegroundService
import com.example.ui.WaveformVisualization
import com.example.ui.theme.MyApplicationTheme
import com.example.util.AppCheckHelper
import com.example.voice.KeywordSpotter
import com.example.voice.VoiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var voiceManager: VoiceManager
    private lateinit var keywordSpotter: KeywordSpotter
    private lateinit var deviceController: DeviceController
    private val aiRepository = GeminiRepository()

    private val database by lazy { VaniDatabase.getDatabase(this) }
    private val messageRepository by lazy { MessageRepository(database.messageDao()) }

    var assistantActive by mutableStateOf(true)
    var isListeningState by mutableStateOf(false)
    var isSpeakingState by mutableStateOf(false)
    var audioAmplitude by mutableStateOf(0.1f)
    var currentSpeechText = mutableStateOf("VANI is listening quietly. Say 'Hey VANI' or tap orb...")
    val commandLogs = mutableStateOf(listOf<LogItem>())

    data class LogItem(val sender: String, val text: String, val isUser: Boolean)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions evaluated quietly
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        AppCheckHelper.configureAppCheck(this, intent)
        deviceController = DeviceController(this)

        voiceManager = VoiceManager(
            context = this,
            onSpeechResult = { text ->
                handleUserCommand(text)
            },
            onWakeWordDetected = {
                speakToUser("Haan jaan, sun rhi hoon, boliye!")
            },
            onAmplitudeChanged = { amp ->
                audioAmplitude = amp
                isListeningState = amp > 0.15f
            }
        )

        keywordSpotter = KeywordSpotter(this) {
            speakToUser("Haan jaan, sun rhi hoon, boliye!")
            voiceManager.startListening()
        }

        requestAllPermissions()
        startVaniService()

        // Observe chat messages from Room database reactively
        lifecycleScope.launch {
            messageRepository.allMessages.collectLatest { entities ->
                commandLogs.value = entities.map { LogItem(it.sender, it.text, it.sender == "You") }
            }
        }

        setContent {
            MyApplicationTheme {
                VaniMainScreen(
                    assistantActive = assistantActive,
                    onToggleAssistant = { active ->
                        assistantActive = active
                        if (active) {
                            startVaniService()
                            keywordSpotter.start()
                            voiceManager.startListening()
                            currentSpeechText.value = "VANI activated. Listening..."
                        } else {
                            stopVaniService()
                            keywordSpotter.stop()
                            voiceManager.stopListening()
                            currentSpeechText.value = "VANI paused."
                        }
                    },
                    isListening = isListeningState,
                    isSpeaking = isSpeakingState,
                    amplitude = audioAmplitude,
                    statusText = currentSpeechText.value,
                    logs = commandLogs.value,
                    onSendCommand = { prompt ->
                        handleUserCommand(prompt)
                    },
                    onTriggerListen = {
                        voiceManager.startListening()
                        currentSpeechText.value = "Listening to you..."
                    },
                    onStopSpeaking = {
                        stopSpeakingNow()
                    },
                    onExportHistory = {
                        exportChatHistory()
                    },
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onTriggerTorch = {
                        val success = deviceController.toggleTorch(null)
                        addLog("Device", if (success) "Torch toggled" else "Torch failed", false)
                    },
                    onAnswerCall = {
                        val success = deviceController.answerCall()
                        addLog("Telecom", if (success) "Call answered" else "No incoming call / permission needed", false)
                    },
                    onRejectCall = {
                        val success = deviceController.endCall()
                        addLog("Telecom", if (success) "Call ended/rejected" else "No active call", false)
                    }
                )
            }
        }

        if (assistantActive) {
            keywordSpotter.start()
            voiceManager.startListening()
        }
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startVaniService() {
        val intent = Intent(this, VaniForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopVaniService() {
        stopService(Intent(this, VaniForegroundService::class.java))
    }

    private fun speakToUser(text: String) {
        currentSpeechText.value = text
        isSpeakingState = true
        voiceManager.speak(text)
        addLog("VANI", text, false)
        handler.postDelayed({
            isSpeakingState = false
        }, 3000)
    }

    private fun stopSpeakingNow() {
        voiceManager.stopSpeaking()
        isSpeakingState = false
        currentSpeechText.value = "Stopped speaking. Waiting for input..."
    }

    private val handler = Handler(Looper.getMainLooper())

    private fun addLog(sender: String, text: String, isUser: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            messageRepository.insertMessage(sender, text)
        }
    }

    private fun exportChatHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            val messages = messageRepository.allMessages.firstOrNull() ?: emptyList()
            val sb = StringBuilder()
            sb.append("=== VANI Assistant Conversation History ===\n")
            sb.append("Created by: Mr. Rinku with Google AI\n")
            sb.append("Exported on: ${Date()}\n\n")
            for (msg in messages) {
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(msg.timestamp))
                sb.append("[$time] ${msg.sender}: ${msg.text}\n")
            }

            try {
                val file = File(cacheDir, "VANI_Chat_History.txt")
                file.writeText(sb.toString())

                val uri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "$packageName.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "VANI Conversation History")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    startActivity(Intent.createChooser(intent, "Export Chat History via"))
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Export failed", e)
            }
        }
    }

    private fun handleUserCommand(command: String) {
        val lower = command.lowercase()
        if (lower.contains("stop") || lower.contains("pause") || lower.contains("chup") || lower.contains("shut up") || lower.contains("shant")) {
            stopSpeakingNow()
            addLog("You", command, true)
            return
        }

        addLog("You", command, true)
        currentSpeechText.value = command

        when {
            lower.contains("torch") || lower.contains("flash") -> {
                deviceController.toggleTorch(true)
                speakToUser("Torch on kar di hai, jaan!")
            }
            lower.contains("battery") -> {
                val level = deviceController.getBatteryLevel()
                speakToUser("Phone ki battery $level percent hai, jaan.")
            }
            lower.contains("call") -> {
                if (lower.contains("answer") || lower.contains("uthao")) {
                    deviceController.answerCall()
                    speakToUser("Call utha li hai, jaan.")
                } else if (lower.contains("cut") || lower.contains("reject") || lower.contains("kaat")) {
                    deviceController.endCall()
                    speakToUser("Call cut kar di hai, jaan.")
                } else {
                    speakToUser("Kise call karu, jaan?")
                }
            }
            lower.contains("whatsapp") -> {
                deviceController.sendWhatsAppMessage("919999999999", "Hello jaan, VANI assistant here!")
                speakToUser("WhatsApp message open kar diya hai.")
            }
            lower.contains("maps") || lower.contains("navigation") -> {
                deviceController.openGoogleMaps("restaurants near me")
                speakToUser("Google Maps open kar diya hai, jaan.")
            }
            lower.contains("volume") -> {
                deviceController.adjustVolume(true)
                speakToUser("Volume badha di hai.")
            }
            else -> {
                val screenSummary = VaniAccessibilityService.instance?.getScreenTextSummary() ?: ""
                val battery = deviceController.getBatteryLevel()
                val contextInfo = "Battery: $battery%. Screen summary: $screenSummary"

                lifecycleScope.launch(Dispatchers.Main) {
                    val systemInstruction = "You are VANI (Virtual Assistant for Native Intelligence), an advanced Android AI assistant created by Mr. Rinku with Google AI. Your personality is friendly, affectionate, and helpful (refer to user warmly as 'jaan' in Hinglish and English). You must ONLY speak when spoken to by the user. Device context: $contextInfo"
                    val aiReply = aiRepository.generateResponse(command, systemInstruction)
                    speakToUser(aiReply)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        keywordSpotter.stop()
        voiceManager.shutdown()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaniMainScreen(
    assistantActive: Boolean,
    onToggleAssistant: (Boolean) -> Unit,
    isListening: Boolean,
    isSpeaking: Boolean,
    amplitude: Float,
    statusText: String,
    logs: List<MainActivity.LogItem>,
    onSendCommand: (String) -> Unit,
    onTriggerListen: () -> Unit,
    onStopSpeaking: () -> Unit,
    onExportHistory: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onTriggerTorch: () -> Unit,
    onAnswerCall: () -> Unit,
    onRejectCall: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("V.A.N.I.", fontWeight = FontWeight.Bold, color = Color.Cyan)
                        Text("Virtual Assistant for Intelligence (Mr. Rinku)", fontSize = 11.sp, color = Color.Gray)
                    }
                },
                actions = {
                    IconButton(onClick = onExportHistory, modifier = Modifier.testTag("export_history_button")) {
                        Icon(Icons.Default.Download, contentDescription = "Export Chat History", tint = Color.Cyan)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                        Text(if (assistantActive) "ON" else "OFF", color = if (assistantActive) Color.Green else Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = assistantActive,
                            onCheckedChange = onToggleAssistant,
                            modifier = Modifier.testTag("assistant_toggle")
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B0F19))
            )
        },
        containerColor = Color(0xFF07090E)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(if (isListening || isSpeaking) scale else 1.0f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF00FFFF), Color(0xFFFF00FF), Color(0xFF0B0F19))
                        )
                    )
                    .border(2.dp, Color.Cyan, CircleShape)
                    .clickable { onTriggerListen() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.Mic,
                    contentDescription = "VANI Voice Orb",
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            WaveformVisualization(
                amplitude = amplitude,
                isActive = isListening || isSpeaking
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131A2A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusText,
                        color = Color(0xFF00FFFF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 2
                    )
                    if (isSpeaking) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onStopSpeaking,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)),
                            modifier = Modifier.testTag("stop_speaking_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = onTriggerTorch,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.weight(1f).padding(end = 4.dp).testTag("torch_button")
                ) {
                    Icon(Icons.Default.FlashlightOn, contentDescription = "Torch", tint = Color.Yellow)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Torch", fontSize = 12.sp)
                }
                Button(
                    onClick = onAnswerCall,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46)),
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp).testTag("answer_call_button")
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Answer", tint = Color.Green)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Answer", fontSize = 12.sp)
                }
                Button(
                    onClick = onRejectCall,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)),
                    modifier = Modifier.weight(1f).padding(start = 4.dp).testTag("reject_call_button")
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "Reject", tint = Color.Red)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reject", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier.fillMaxWidth().testTag("accessibility_settings_button"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Magenta)
            ) {
                Icon(Icons.Default.Accessibility, contentDescription = "Accessibility")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enable Screen Automation (Accessibility)")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Ask VANI anything (e.g. Call Mom)...", color = Color.Gray) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Cyan,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            onSendCommand(textInput)
                            textInput = ""
                        }
                    },
                    modifier = Modifier
                        .background(Color.Cyan, CircleShape)
                        .testTag("send_button")
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Persistent Chat History (Room DB)",
                color = Color.Gray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs) { log ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (log.isUser) Color(0xFF1E293B) else Color(0xFF1E1B4B)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = log.sender,
                                color = if (log.isUser) Color.Cyan else Color.Magenta,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = log.text,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
