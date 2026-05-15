package com.neo.di

import android.content.Context
import com.neo.data.dao.BlockedUserDao
import com.neo.data.dao.CommentDao
import com.neo.data.dao.DeviceDao
import com.neo.data.dao.NotificationDao
import com.neo.data.dao.PostDao
import com.neo.data.dao.ReactionDao
import com.neo.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Singleton

/**
 * Hilt module for providing database dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }
    
    @Provides
    fun providePostDao(database: AppDatabase): PostDao {
        return database.postDao()
    }
    
    @Provides
    fun provideDeviceDao(database: AppDatabase): DeviceDao {
        return database.deviceDao()
    }
    
    @Provides
    fun provideBlockedUserDao(database: AppDatabase): BlockedUserDao {
        return database.blockedUserDao()
    }
    
    @Provides
    fun provideCommentDao(database: AppDatabase): CommentDao {
        return database.commentDao()
    }
    
    @Provides
    fun provideReactionDao(database: AppDatabase): ReactionDao {
        return database.reactionDao()
    }

    @Provides
    fun provideNotificationDao(database: AppDatabase): NotificationDao {
        return database.notificationDao()
    }
}
