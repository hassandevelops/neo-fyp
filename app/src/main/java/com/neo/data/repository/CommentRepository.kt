package com.neo.data.repository

import com.neo.data.dao.CommentDao
import com.neo.data.model.Comment
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing comments.
 * Provides abstraction layer between data sources and domain layer.
 */
@Singleton
class CommentRepository @Inject constructor(
    private val commentDao: CommentDao
) {
    
    /**
     * Insert a new comment.
     */
    suspend fun insertComment(comment: Comment) {
        commentDao.insert(comment)
    }
    
    /**
     * Insert multiple comments.
     */
    suspend fun insertComments(comments: List<Comment>) {
        commentDao.insertAll(comments)
    }
    
    /**
     * Get all top-level comments for a post.
     */
    fun getTopLevelCommentsForPost(postId: String): Flow<List<Comment>> {
        return commentDao.getTopLevelCommentsForPost(postId)
    }
    
    /**
     * Get all replies for a specific comment.
     */
    fun getRepliesForComment(commentId: String): Flow<List<Comment>> {
        return commentDao.getRepliesForComment(commentId)
    }

    /**
     * Get every comment (top-level + replies) for a post in a single stream.
     */
    fun getAllCommentsForPost(postId: String): Flow<List<Comment>> {
        return commentDao.getAllCommentsForPost(postId)
    }
    
    /**
     * Get a specific comment by ID.
     */
    suspend fun getCommentById(commentId: String): Comment? {
        return commentDao.getCommentById(commentId)
    }
    
    /**
     * Get total comment count for a post.
     */
    suspend fun getCommentCountForPost(postId: String): Int {
        return commentDao.getCommentCountForPost(postId)
    }
    
    /**
     * Get comment count as Flow for reactive updates.
     */
    fun getCommentCountForPostFlow(postId: String): Flow<Int> {
        return commentDao.getCommentCountForPostFlow(postId)
    }
    
    /**
     * Check if a comment exists.
     */
    suspend fun commentExists(commentId: String): Boolean {
        return commentDao.commentExists(commentId)
    }
    
    /**
     * Delete a comment.
     */
    suspend fun deleteComment(commentId: String) {
        commentDao.deleteById(commentId)
    }
    
    /**
     * Delete all comments for a post.
     */
    suspend fun deleteCommentsForPost(postId: String) {
        commentDao.deleteCommentsForPost(postId)
    }
    
    /**
     * Delete old comments (for cleanup).
     * Optionally exclude comments from a specific author.
     */
    suspend fun deleteOldComments(beforeTimestamp: Long, excludeAuthorId: String? = null): Int {
        return if (excludeAuthorId != null) {
            commentDao.deleteOldCommentsExcludingAuthor(beforeTimestamp, excludeAuthorId)
        } else {
            commentDao.deleteOldComments(beforeTimestamp)
        }
    }
    
    /**
     * Get total comment count across all posts.
     */
    suspend fun getTotalCommentCount(): Int {
        return commentDao.getTotalCommentCount()
    }
}
