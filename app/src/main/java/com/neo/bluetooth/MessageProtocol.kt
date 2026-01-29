package com.neo.bluetooth

import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * Handles serialization and deserialization of messages.
 */
object MessageProtocol {
    
    private val gson = Gson()
    
    private const val TYPE_HANDSHAKE = "handshake"
    private const val TYPE_POST_BROADCAST = "post_broadcast"
    private const val TYPE_SYNC_REQUEST = "sync_request"
    private const val TYPE_SYNC_RESPONSE = "sync_response"
    private const val TYPE_ACK = "ack"
    
    /**
     * Serialize a message to JSON string.
     */
    fun serialize(message: Message): String {
        val json = JsonObject()
        
        when (message) {
            is Message.Handshake -> {
                json.addProperty("type", TYPE_HANDSHAKE)
                json.addProperty("deviceId", message.deviceId)
                json.addProperty("deviceName", message.deviceName)
                json.addProperty("publicKey", message.publicKey)
            }
            is Message.PostBroadcast -> {
                json.addProperty("type", TYPE_POST_BROADCAST)
                json.addProperty("id", message.id)
                json.addProperty("authorId", message.authorId)
                json.addProperty("authorName", message.authorName)
                json.addProperty("content", message.content)
                json.addProperty("timestamp", message.timestamp)
                json.addProperty("signature", message.signature)
                json.addProperty("publicKey", message.publicKey)
                json.addProperty("ttl", message.ttl)
            }
            is Message.SyncRequest -> {
                json.addProperty("type", TYPE_SYNC_REQUEST)
                json.addProperty("lastTimestamp", message.lastTimestamp)
            }
            is Message.SyncResponse -> {
                json.addProperty("type", TYPE_SYNC_RESPONSE)
                json.add("posts", gson.toJsonTree(message.posts))
            }
            is Message.Ack -> {
                json.addProperty("type", TYPE_ACK)
                json.addProperty("messageId", message.messageId)
            }
        }
        
        return json.toString()
    }
    
    /**
     * Deserialize a JSON string to a message.
     */
    fun deserialize(jsonString: String): Message? {
        return try {
            val json = gson.fromJson(jsonString, JsonObject::class.java)
            val type = json.get("type")?.asString ?: return null
            
            when (type) {
                TYPE_HANDSHAKE -> {
                    Message.Handshake(
                        deviceId = json.get("deviceId").asString,
                        deviceName = json.get("deviceName").asString,
                        publicKey = json.get("publicKey").asString
                    )
                }
                TYPE_POST_BROADCAST -> {
                    Message.PostBroadcast(
                        id = json.get("id").asString,
                        authorId = json.get("authorId").asString,
                        authorName = json.get("authorName").asString,
                        content = json.get("content").asString,
                        timestamp = json.get("timestamp").asLong,
                        signature = json.get("signature").asString,
                        publicKey = json.get("publicKey").asString,
                        ttl = json.get("ttl").asInt
                    )
                }
                TYPE_SYNC_REQUEST -> {
                    Message.SyncRequest(
                        lastTimestamp = json.get("lastTimestamp").asLong
                    )
                }
                TYPE_SYNC_RESPONSE -> {
                    val postsArray = json.getAsJsonArray("posts")
                    val posts = postsArray.map { element ->
                        gson.fromJson(element, Message.PostBroadcast::class.java)
                    }
                    Message.SyncResponse(posts)
                }
                TYPE_ACK -> {
                    Message.Ack(
                        messageId = json.get("messageId").asString
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
