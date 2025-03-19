package org.example.utils

import dev.inmo.tgbotapi.types.message.abstracts.PrivateContentMessage
import dev.inmo.tgbotapi.types.message.content.PhotoContent
import java.util.concurrent.ConcurrentHashMap

class MessageService {
    val messages = ConcurrentHashMap<String, PrivateContentMessage<PhotoContent>>()

    fun addMessage(uuid: String, message: PrivateContentMessage<PhotoContent>) {
        messages.put(uuid, message)
    }

    fun getMessage(uuid: String): PrivateContentMessage<PhotoContent> {
        return messages.getValue(uuid)
    }
    
    fun removeMessage(uuid: String) {
        messages.remove(uuid)
    }
}