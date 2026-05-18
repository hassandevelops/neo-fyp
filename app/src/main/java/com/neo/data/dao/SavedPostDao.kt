package com.neo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neo.data.model.SavedPost
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPostDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(savedPost: SavedPost)

    @Query("DELETE FROM saved_posts WHERE postId = :postId")
    suspend fun delete(postId: String)

    @Query("SELECT postId FROM saved_posts ORDER BY savedAt DESC")
    fun observeAllSavedPostIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_posts WHERE postId = :postId)")
    fun isSaved(postId: String): Flow<Boolean>

    @Query("DELETE FROM saved_posts")
    suspend fun deleteAll()
}
