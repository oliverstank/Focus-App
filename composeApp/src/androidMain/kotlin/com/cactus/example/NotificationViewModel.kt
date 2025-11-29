package com.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cactus.CactusLM
import com.cactus.CactusInitParams
import com.cactus.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _todayNotifications = MutableStateFlow<List<NotificationData>>(emptyList())
    val todayNotifications: StateFlow<List<NotificationData>> = _todayNotifications.asStateFlow()

    private val cactusLM = CactusLM()

    init {
        viewModelScope.launch {
            NotificationRepository.notifications.collect { notifications ->
                _todayNotifications.value = NotificationRepository.getTodayNotifications()
            }
        }
    }

    fun summarizeNotifications() {
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading("Preparing...")

                val notifications = NotificationRepository.getTodayNotifications()
                if (notifications.isEmpty()) {
                    _uiState.value = UiState.Error("No notifications to summarize")
                    return@launch
                }

                // Initialize model if not already loaded
                if (!cactusLM.isLoaded()) {
                    _uiState.value = UiState.Loading("Downloading model...")
                    cactusLM.downloadModel("qwen3-0.6")

                    _uiState.value = UiState.Loading("Initializing model...")
                    cactusLM.initializeModel(CactusInitParams(model = "qwen3-0.6", contextSize = 2048))
                }

                // Format notifications for the prompt
                val notificationText = notifications.joinToString("\n\n") { notification ->
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(notification.timestamp))
                    "[$time] ${notification.packageName}\nTitle: ${notification.title}\nMessage: ${notification.text}"
                }

                val prompt = """
                    You are a helpful assistant that summarizes notifications.
                    First, in a [THINKING] block, analyze the notifications. Identify the most important ones, any patterns or trends, and suggest potential actions. End the thinking block with [/THINKING].
                    Then, in a [SUMMARY] block, provide a concise and actionable summary for the user based on your analysis. The summary should be clear and easy to read. End the summary block with [/SUMMARY].

                    Here are the notifications:

                    $notificationText
                """.trimIndent()

                _uiState.value = UiState.Loading("Generating summary...")
                val result = cactusLM.generateCompletion(
                    messages = listOf(ChatMessage(content = prompt, role = "user"))
                )

                if (result?.success == true) {
                    val fullResponse = result.response ?: "No summary generated"
                    val summary = fullResponse.substringAfter("[SUMMARY]", fullResponse).substringBefore("[/SUMMARY]").trim()
                    _uiState.value = UiState.Success(summary)
                } else {
                    _uiState.value = UiState.Error("Failed to generate summary")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    fun clearNotifications() {
        NotificationRepository.clearAllNotifications()
        _uiState.value = UiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        cactusLM.unload()
    }

    sealed class UiState {
        object Idle : UiState()
        data class Loading(val message: String = "Loading...") : UiState()
        data class Success(val summary: String) : UiState()
        data class Error(val message: String) : UiState()
    }
}