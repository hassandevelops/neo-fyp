# Data Architecture & Schema: Neo

## 1. Database Overview
The application uses Android Room for persistence. Given the decentralized nature of the app, the database acts as the single source of truth for all synchronized data.

## 2. Entity Relationship Diagram (Conceptual)
*   **Device (Node)** `1 : N` **Post** (Author)
*   **Post** `1 : N` **Comment**
*   **Post** `1 : N` **Reaction**
*   **Comment** `1 : N` **Comment** (Replies via `parentCommentId`)

## 3. Schema Definitions

### 3.1. Post Entity (`posts`)
```kotlin
@Entity(tableName = "posts", indices = [Index("timestamp"), Index("authorId")])
data class Post(
    @PrimaryKey val id: String, // UUID
    val authorId: String,       // Device ID / Public Key Hash
    val authorName: String,
    val content: String,
    
    // Media - REFACTORED
    val imageUri: String?,      // Internal file path (e.g., file:///data/user/0/.../images/post_123.jpg)
    // val imageData: String?   // DELETED: No Base64 in DB
    val imageHash: String?,     // SHA-256 for verification
    val imageSize: Int?,
    val imageWidth: Int?,
    val imageHeight: Int?,
    
    // Sync & Crypto Metadata
    val timestamp: Long,
    val signature: String,      // Ed25519 Signature
    val publicKey: String,      // Ed25519 Public Key
    val ttl: Int,               // Gossip Time-to-Live
    val firstSeenTimestamp: Long
)
```

### 3.2. Comment Entity (`comments`)
```kotlin
@Entity(
    tableName = "comments", 
    indices = [Index("postId"), Index("parentCommentId")],
    foreignKeys = [
        ForeignKey(entity = Post::class, parentColumns = ["id"], childColumns = ["postId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class Comment(
    @PrimaryKey val id: String,
    val postId: String,
    val parentCommentId: String?, // Nullable for top-level comments
    val authorId: String,
    val authorName: String,
    val content: String,
    val timestamp: Long,
    val signature: String,
    val publicKey: String,
    val ttl: Int,
    val firstSeenTimestamp: Long
)
```

### 3.3. Reaction Entity (`reactions`)
```kotlin
@Entity(
    tableName = "reactions",
    primaryKeys = ["postId", "userId", "type"], // Composite Primary Key
    foreignKeys = [
        ForeignKey(entity = Post::class, parentColumns = ["id"], childColumns = ["postId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class Reaction(
    val id: String, // Broadcast ID
    val postId: String,
    val userId: String,
    val userName: String,
    val type: ReactionType, // Enum: LIKE, HEART, etc.
    val timestamp: Long,
    val signature: String,
    val publicKey: String,
    val ttl: Int,
    val firstSeenTimestamp: Long
)
```

## 4. Required Data Migrations
**Migration Strategy:** `fallbackToDestructiveMigration()` MUST be removed.

**Migration 1 to 2 (Image Storage Refactor):**
1.  Iterate through all rows in the `posts` table where `imageData` IS NOT NULL.
2.  Decode the Base64 `imageData` into a byte array.
3.  Write the byte array to the application's internal files directory (`Context.filesDir/media/images/`).
4.  Create a new `posts_temp` table without the `imageData` column but including the new `imageUri` column.
5.  Copy data from `posts` to `posts_temp`, inserting the newly generated file paths into `imageUri`.
6.  Drop the old `posts` table.
7.  Rename `posts_temp` to `posts`.
8.  Recreate indices.

## 5. Storage Management
*   **Media Cleanup:** Images associated with posts that have been deleted or expired must be physically deleted from `Context.filesDir` to prevent storage leaks. `StorageManager.kt` handles vacuuming orphaned files.
