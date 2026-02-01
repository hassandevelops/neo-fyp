package com.neo.data.dao

import androidx.room.*
import com.neo.data.model.Post
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Post operations.
 */
@Dao
interface PostDao {
    
    /**
     * Insert a post. Ignores if post with same ID already exists.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(post: Post): Long
    
    /**
     * Insert multiple posts.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(posts: List<Post>)
    
    /**
     * Get all posts ordered by timestamp (newest first).
     * Returns a Flow for reactive updates.
     */
    @Query("SELECT * FROM posts ORDER BY timestamp DESC")
    fun getAllPosts(): Flow<List<Post>>
    
    /**
     * Get posts created after a specific timestamp.
     * Used for sync operations.
     */
    @Query("SELECT * FROM posts WHERE timestamp > :afterTimestamp ORDER BY timestamp ASC")
    suspend fun getPostsAfter(afterTimestamp: Long): List<Post>
    
    /**
     * Get a specific post by ID.
     */
    @Query("SELECT * FROM posts WHERE id = :postId")
    suspend fun getPostById(postId: String): Post?
    
    /**
     * Check if a post exists.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM posts WHERE id = :postId)")
    suspend fun postExists(postId: String): Boolean
    
    /**
     * Delete posts older than a specific timestamp.
     * Used for garbage collection.
     */
    @Query("DELETE FROM posts WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldPosts(beforeTimestamp: Long): Int
    
    /**
     * Delete posts older than a specific timestamp, excluding posts from a specific author.
     * Used for cleanup while preserving user's own posts.
     */
    @Query("DELETE FROM posts WHERE timestamp < :beforeTimestamp AND authorId != :excludeAuthorId")
    suspend fun deleteOldPostsExcludingAuthor(beforeTimestamp: Long, excludeAuthorId: String): Int
    
    /**
     * Get total post count.
     */
    @Query("SELECT COUNT(*) FROM posts")
    suspend fun getPostCount(): Int
    
    /**
     * Delete all posts (for testing/debugging).
     */
    @Query("DELETE FROM posts")
    suspend fun deleteAll()
    
    /**
     * Update an existing post.
     */
    @Update
    suspend fun update(post: Post)
}
