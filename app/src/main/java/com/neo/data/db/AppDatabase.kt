package com.neo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.neo.data.dao.DeviceDao
import com.neo.data.dao.PostDao
import com.neo.data.dao.BlockedUserDao
import com.neo.data.dao.CommentDao
import com.neo.data.dao.ReactionDao
import com.neo.data.model.Device
import com.neo.data.model.Post
import com.neo.data.model.BlockedUser
import com.neo.data.model.Comment
import com.neo.data.model.Reaction

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
        Reaction::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun postDao(): PostDao
    abstract fun deviceDao(): DeviceDao
    abstract fun blockedUserDao(): BlockedUserDao
    abstract fun commentDao(): CommentDao
    abstract fun reactionDao(): ReactionDao

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
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS reactions (
                        id TEXT NOT NULL PRIMARY KEY,
                        postId TEXT NOT NULL,
                        authorId TEXT NOT NULL,
                        authorName TEXT NOT NULL,
                        reactionType TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        signature TEXT NOT NULL,
                        publicKey TEXT NOT NULL,
                        FOREIGN KEY(postId) REFERENCES posts(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_reactions_postId ON reactions(postId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_reactions_authorId ON reactions(authorId)")
            }
        }

        // Migration from version 5 to 6 - Remove deprecated imageData/imageUri columns
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Check if old columns exist and remove them (they were replaced by imageHash)
                try {
                    database.execSQL("ALTER TABLE posts DROP COLUMN imageData")
                } catch (e: Exception) {
                    // Column may not exist, ignore
                }
                try {
                    database.execSQL("ALTER TABLE posts DROP COLUMN imageUri")
                } catch (e: Exception) {
                    // Column may not exist, ignore
                }
            }
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
                        MIGRATION_5_6
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}