package com.neo.bluetooth

import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Handles serialization and deserialization of messages using kotlinx.serialization.
 */
object MessageProtocol {

    private const val TAG = "MessageProtocol"

    private val messageSerializersModule = SerializersModule {
        polymorphic(Message::class) {
            subclass(Message.Handshake::class)
            subclass(Message.PostBroadcast::class)
            subclass(Message.EventSyncRequest::class)
            subclass(Message.EventSyncResponse::class)
            subclass(Message.Ack::class)
            subclass(Message.ImageMetadata::class)
            subclass(Message.ImageChunk::class)
            subclass(Message.CommentBroadcast::class)
            subclass(Message.ReactionBroadcast::class)
            subclass(Message.PeerExchange::class)
        }
    }

    private val jsonWithPolymorphism = Json {
        serializersModule = messageSerializersModule
        classDiscriminator = "kind"
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    /**
     * Serialize a message to JSON string.
     */
    fun serialize(message: Message): String {
        return try {
            jsonWithPolymorphism.encodeToString(message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize message", e)
            "{}"
        }
    }

    /**
     * Deserialize a JSON string to a message.
     */
    fun deserialize(jsonString: String): Message? {
        return try {
            jsonWithPolymorphism.decodeFromString<Message>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize message", e)
            null
        }
    }
}
