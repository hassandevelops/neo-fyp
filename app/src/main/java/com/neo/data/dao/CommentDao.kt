package com.neo.data.dao

import androidx.room.*
import com.neo.data.model.Comment
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Comment entities.
 */
@Dao
interface CommentDao {
    
    /**
     * Insert a new comment.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(comment: Comment)
    
    /**
     * Insert multiple comments.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(comments: List<Comment>)
    
    /**
     * Get all top-level comments for a post (no parent).
     */
    @Query("SELECT * FROM comments WHERE postId = :postId AND parentCommentId IS NULL ORDER BY timestamp DESC")
    fun getTopLevelCommentsForPost(postId: String): Flow<List<Comment>>
    
    /**
     * Get all replies for a specific comment.
     */
    @Query("SELECT * FROM comments WHERE parentCommentId = :parentCommentId ORDER BY timestamp ASC")
    fun getRepliesForComment(parentCommentId: String): Flow<List<Comment>>

    /**
     * Get every comment (top-level + replies) for a post in one stream.
     * Used to build threaded views in a single subscription instead of
     * one Flow per comment.
     */
    @Query("SELECT * FROM comments WHERE postId = :postId ORDER BY timestamp ASC")
    fun getAllCommentsForPost(postId: String): Flow<List<Comment>>
    
    /**
     * Get a specific comment by ID.
     */
    @Query("SELECT * FROM comments WHERE id = :commentId")
    suspend fun getCommentById(commentId: String): Comment?
    
    /**
     * Get total comment count for a post (including replies).
     */
    @Query("SELECT COUNT(*) FROM comments WHERE postId = :postId")
    suspend fun getCommentCountForPost(postId: String): Int
    
    /**
     * Get total comment count for a post as Flow.
     */
    @Query("SELECT COUNT(*) FROM comments WHERE postId = :postId")
    fun getCommentCountForPostFlow(postId: String): Flow<Int>
    
    /**
     * Check if a comment exists.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM comments WHERE id = :commentId)")
    suspend fun commentExists(commentId: String): Boolean
    
    /**
     * Delete a comment by ID.
     */
    @Query("DELETE FROM comments WHERE id = :commentId")
    suspend fun deleteById(commentId: String)
    
    /**
     * Delete all comments for a post.
     * Note: Cascade delete should handle this automatically.
     */
    @Query("DELETE FROM comments WHERE postId = :postId")
    suspend fun deleteCommentsForPost(postId: String)
    
    /**
     * Delete old comments (for cleanup).
     */
    @Query("DELETE FROM comments WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldComments(beforeTimestamp: Long): Int
    
    /**
     * Delete old comments excluding specific author.
     */
    @Query("DELETE FROM comments WHERE timestamp < :beforeTimestamp AND authorId != :excludeAuthorId")
    suspend fun deleteOldCommentsExcludingAuthor(beforeTimestamp: Long, excludeAuthorId: String): Int
    
    /**
     * Get total comment count.
     */
    @Query("SELECT COUNT(*) FROM comments")
    suspend fun getTotalCommentCount(): Int
    
    /**
     * Delete all comments (for testing).
     */
    @Query("DELETE FROM comments")
    suspend fun deleteAll()
}
