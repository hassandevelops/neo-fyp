package com.neo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.neo.data.dao.DeviceDao
import com.neo.data.dao.PostDao
import com.neo.data.dao.BlockedUserDao
import com.neo.data.dao.CommentDao
import com.neo.data.dao.NotificationDao
import com.neo.data.dao.ReactionDao
import com.neo.data.dao.SavedPostDao
import com.neo.data.dao.EventLogDao
import com.neo.data.dao.PeerProfileDao
import com.neo.data.dao.FollowDao
import com.neo.data.model.Device
import com.neo.data.model.EventLog
import com.neo.data.model.Post
import com.neo.data.model.BlockedUser
import com.neo.data.model.Comment
import com.neo.data.model.Reaction
import com.neo.data.model.Notification
import com.neo.data.model.SavedPost
import com.neo.data.model.PeerProfile
import com.neo.data.model.Follow

/**
 * Room database for Neo app.
 * Contains posts and devices tables.
 */
@Database(
    entities = [
        Post::class,
        Device::class,
        BlockedUser::class,
        Comment::class,
        Reaction::class,
        Notification::class,
        SavedPost::class,
        EventLog::class,
        PeerProfile::class,
        Follow::class
    ],
    version = 14,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun postDao(): PostDao
    abstract fun deviceDao(): DeviceDao
    abstract fun blockedUserDao(): BlockedUserDao
    abstract fun commentDao(): CommentDao
    abstract fun reactionDao(): ReactionDao
    abstract fun notificationDao(): NotificationDao
    abstract fun savedPostDao(): SavedPostDao
    abstract fun eventLogDao(): EventLogDao
    abstract fun peerProfileDao(): PeerProfileDao
    abstract fun followDao(): FollowDao

    companion object {
        const val DATABASE_NAME = "neo_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to 2 - Add new fields for posts
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE posts ADD COLUMN imageHash TEXT")
                database.execSQL("ALTER TABLE posts ADD COLUMN imageSize INTEGER")
                database.execSQL("ALTER TABLE posts ADD COLUMN imageWidth INTEGER")
                database.execSQL("ALTER TABLE posts ADD COLUMN imageHeight INTEGER")
            }
        }

        // Migration from version 2 to 3 - Add indices
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_posts_timestamp ON posts(timestamp)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_posts_authorId ON posts(authorId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_posts_firstSeenTimestamp ON posts(firstSeenTimestamp)")
            }
        }

        // Migration from version 3 to 4 - Add comments table
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS comments (
                        id TEXT NOT NULL PRIMARY KEY,
                        postId TEXT NOT NULL,
                        parentCommentId TEXT,
                        authorId TEXT NOT NULL,
                        authorName TEXT NOT NULL,
                        content TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        signature TEXT NOT NULL,
                        publicKey TEXT NOT NULL,
                        ttl INTEGER NOT NULL,
                        firstSeenTimestamp INTEGER NOT NULL,
                        FOREIGN KEY(postId) REFERENCES posts(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_comments_postId ON comments(postId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_comments_parentCommentId ON comments(parentCommentId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_comments_timestamp ON comments(timestamp)")
            }
        }

        // Migration from version 4 to 5 - Add reactions table
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                createCurrentReactionsTable(database)
            }
        }

        // Migration from version 5 to 6 - Remove deprecated imageData/imageUri columns
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val postColumns = getTableColumns(database, "posts")
                if ("imageData" in postColumns || "imageUri" in postColumns) {
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS posts_new (
                            id TEXT NOT NULL PRIMARY KEY,
                            authorId TEXT NOT NULL,
                            authorName TEXT NOT NULL,
                            content TEXT NOT NULL,
                            imageHash TEXT,
                            imageSize INTEGER,
                            imageWidth INTEGER,
                            imageHeight INTEGER,
                            timestamp INTEGER NOT NULL,
                            signature TEXT NOT NULL,
                            publicKey TEXT NOT NULL,
                            ttl INTEGER NOT NULL,
                            firstSeenTimestamp INTEGER NOT NULL
                        )
                    """.trimIndent())
                    database.execSQL("""
                        INSERT INTO posts_new (
                            id, authorId, authorName, content, imageHash, imageSize, imageWidth,
                            imageHeight, timestamp, signature, publicKey, ttl, firstSeenTimestamp
                        )
                        SELECT
                            id, authorId, authorName, content, imageHash, imageSize, imageWidth,
                            imageHeight, timestamp, signature, publicKey, ttl, firstSeenTimestamp
                        FROM posts
                    """.trimIndent())
                    database.execSQL("DROP TABLE posts")
                    database.execSQL("ALTER TABLE posts_new RENAME TO posts")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_posts_timestamp ON posts(timestamp)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_posts_authorId ON posts(authorId)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_posts_firstSeenTimestamp ON posts(firstSeenTimestamp)")
                }

                val reactionColumns = getTableColumns(database, "reactions")
                if (reactionColumns.isNotEmpty() && "userId" !in reactionColumns) {
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS reactions_new (
                            id TEXT NOT NULL PRIMARY KEY,
                            postId TEXT NOT NULL,
                            userId TEXT NOT NULL,
                            userName TEXT NOT NULL,
                            type TEXT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            signature TEXT NOT NULL,
                            publicKey TEXT NOT NULL,
                            ttl INTEGER NOT NULL,
                            firstSeenTimestamp INTEGER NOT NULL,
                            FOREIGN KEY(postId) REFERENCES posts(id) ON DELETE CASCADE
                        )
                    """.trimIndent())
                    database.execSQL("""
                        INSERT INTO reactions_new (
                            id, postId, userId, userName, type, timestamp, signature, publicKey,
                            ttl, firstSeenTimestamp
                        )
                        SELECT
                            id, postId, authorId, authorName, reactionType, timestamp, signature,
                            publicKey, 5, timestamp
                        FROM reactions
                    """.trimIndent())
                    database.execSQL("DROP TABLE reactions")
                    database.execSQL("ALTER TABLE reactions_new RENAME TO reactions")
                    createCurrentReactionIndexes(database)
                }
            }
        }

        private fun createCurrentReactionsTable(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS reactions (
                    id TEXT NOT NULL PRIMARY KEY,
                    postId TEXT NOT NULL,
                    userId TEXT NOT NULL,
                    userName TEXT NOT NULL,
                    type TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    signature TEXT NOT NULL,
                    publicKey TEXT NOT NULL,
                    ttl INTEGER NOT NULL,
                    firstSeenTimestamp INTEGER NOT NULL,
                    FOREIGN KEY(postId) REFERENCES posts(id) ON DELETE CASCADE
                )
            """.trimIndent())
            createCurrentReactionIndexes(database)
        }

        private fun createCurrentReactionIndexes(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_reactions_unique ON reactions(postId, userId, type)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_reactions_postId ON reactions(postId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_reactions_timestamp ON reactions(timestamp)")
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS notifications (
                        id TEXT NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL,
                        message TEXT NOT NULL,
                        postId TEXT,
                        authorName TEXT,
                        timestamp INTEGER NOT NULL,
                        isRead INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_timestamp ON notifications(timestamp)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_isRead ON notifications(isRead)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS saved_posts (
                        postId TEXT NOT NULL PRIMARY KEY,
                        savedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_saved_posts_savedAt ON saved_posts(savedAt)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE posts ADD COLUMN keyAlgorithm TEXT NOT NULL DEFAULT 'Ed25519'")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS event_log (
                        eventId TEXT NOT NULL PRIMARY KEY,
                        authorDid TEXT NOT NULL,
                        sequenceNum INTEGER NOT NULL,
                        eventType TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        signature TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_event_log_author_seq ON event_log(authorDid, sequenceNum)")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE posts ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE posts ADD COLUMN eventId TEXT")
                database.execSQL("ALTER TABLE posts ADD COLUMN authorDid TEXT")
                database.execSQL("ALTER TABLE posts ADD COLUMN sequenceNum INTEGER")
                database.execSQL("ALTER TABLE posts ADD COLUMN eventType TEXT")
                database.execSQL("ALTER TABLE posts ADD COLUMN eventSignature TEXT")
                database.execSQL("ALTER TABLE posts ADD COLUMN eventPayload TEXT")
            }
        }

        // Add optional human-readable location attached to a post.
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE posts ADD COLUMN locationName TEXT")
            }
        }

        // Profile sync (peer_profiles) + follow graph (follows) + notification
        // navigation columns. See PeerProfile / Follow / Notification models.
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS peer_profiles (
                        did TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        bio TEXT NOT NULL DEFAULT '',
                        avatarPath TEXT,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS follows (
                        id TEXT NOT NULL PRIMARY KEY,
                        followerDid TEXT NOT NULL,
                        followeeDid TEXT NOT NULL,
                        active INTEGER NOT NULL DEFAULT 1,
                        timestamp INTEGER NOT NULL,
                        signature TEXT NOT NULL,
                        publicKey TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_follows_unique ON follows(followerDid, followeeDid)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_follows_followeeDid ON follows(followeeDid)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_follows_followerDid ON follows(followerDid)")

                database.execSQL("ALTER TABLE notifications ADD COLUMN targetUserId TEXT")
                database.execSQL("ALTER TABLE notifications ADD COLUMN actorId TEXT")
            }
        }

        private fun getTableColumns(database: SupportSQLiteDatabase, tableName: String): Set<String> {
            val columns = mutableSetOf<String>()
            database.query("PRAGMA table_info($tableName)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    columns += cursor.getString(nameIndex)
                }
            }
            return columns
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "neo_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
