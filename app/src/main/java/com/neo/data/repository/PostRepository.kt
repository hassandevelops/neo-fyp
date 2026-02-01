package com.neo.data.repository

import com.neo.data.dao.PostDao
import com.neo.data.model.Post
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Post data operations.
 * Abstracts data source from the rest of the app.
 */
@Singleton
class PostRepository @Inject constructor(
    private val postDao: PostDao
) {
    
    /**
     * Get all posts as a Flow (reactive).
     */
    fun getAllPosts(): Flow<List<Post>> = postDao.getAllPosts()
    
    /**
     * Insert a new post.
     * Returns true if inserted, false if already exists.
     */
    suspend fun insertPost(post: Post): Boolean {
        val result = postDao.insert(post)
        return result != -1L
    }
    
    /**
     * Insert multiple posts.
     */
    suspend fun insertPosts(posts: List<Post>) {
        postDao.insertAll(posts)
    }
    
    /**
     * Get posts created after a specific timestamp.
     */
    suspend fun getPostsAfter(timestamp: Long): List<Post> {
        return postDao.getPostsAfter(timestamp)
    }
    
    /**
     * Check if a post exists.
     */
    suspend fun postExists(postId: String): Boolean {
        return postDao.postExists(postId)
    }
    
    /**
     * Get a specific post by ID.
     */
    suspend fun getPostById(postId: String): Post? {
        return postDao.getPostById(postId)
    }
    
    /**
     * Delete posts older than the specified timestamp.
     * Optionally exclude posts from a specific author.
     */
    suspend fun deleteOldPosts(beforeTimestamp: Long, excludeAuthorId: String? = null): Int {
        return if (excludeAuthorId != null) {
            postDao.deleteOldPostsExcludingAuthor(beforeTimestamp, excludeAuthorId)
        } else {
            postDao.deleteOldPosts(beforeTimestamp)
        }
    }
    
    /**
     * Get total post count.
     */
    suspend fun getPostCount(): Int {
        return postDao.getPostCount()
    }
    
    /**
     * Update an existing post.
     */
    suspend fun updatePost(post: Post) {
        postDao.update(post)
    }
}
