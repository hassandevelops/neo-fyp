package com.neo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.neo.data.dao.DeviceDao
import com.neo.data.dao.PostDao
import com.neo.data.model.Device
import com.neo.data.model.Post

/**
 * Room database for Neo app.
 * Contains posts and devices tables.
 */
@Database(
    entities = [Post::class, Device::class],
    version = 2, // Incremented for imageUri field
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun postDao(): PostDao
    abstract fun deviceDao(): DeviceDao
    
    companion object {
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
