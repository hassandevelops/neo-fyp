package com.neo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 6, // Incremented for Reaction table
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
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "neo_database"
                )
                    .fallbackToDestructiveMigration() // Allow schema changes during development
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
