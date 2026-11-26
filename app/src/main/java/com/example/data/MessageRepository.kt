package com.example.data

import kotlinx.coroutines.flow.Flow

class MessageRepository(private val messageDao: MessageDao) {
    val allMessages: Flow<List<ChatMessageEntity>> = messageDao.getAllMessages()

    suspend fun insertMessage(sender: String, text: String) {
        messageDao.insertMessage(ChatMessageEntity(sender = sender, text = text))
    }

    suspend fun clearHistory() {
        messageDao.clearAllMessages()
    }
}
