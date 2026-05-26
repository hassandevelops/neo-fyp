package com.neo.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.neo.data.dao.PostDao
import com.neo.data.dao.PostStatsEntry
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

    fun getPostsByAuthor(authorId: String): Flow<List<Post>> = postDao.getPostsByAuthor(authorId)

    /**
     * Get all posts once for non-reactive operations.
     */
    suspend fun getAllPostsList(): List<Post> = postDao.getAllPostsList()

    /**
     * Get all image hashes referenced by posts.
     */
    suspend fun getReferencedImageHashes(): Set<String> {
        return postDao.getReferencedImageHashes().toSet()
    }

    /**
     * Get paginated posts for infinite scrolling.
     */
    fun getPostsPaged(): Flow<PagingData<Post>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            prefetchDistance = PREFETCH_DISTANCE,
            enablePlaceholders = false,
            initialLoadSize = INITIAL_LOAD_SIZE
        ),
        pagingSourceFactory = { postDao.getPostsPaged() }
    ).flow

    /**
     * Get posts with manual pagination.
     */
    suspend fun getPostsPaginated(page: Int, pageSize: Int = PAGE_SIZE): List<Post> {
        val offset = (page - 1) * pageSize
        return postDao.getPostsPaginated(pageSize, offset)
    }
    
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
     * Get posts created after a specific timestamp (author clock).
     */
    suspend fun getPostsAfter(timestamp: Long): List<Post> {
        return postDao.getPostsAfter(timestamp)
    }

    /**
     * Get posts first received after a specific timestamp (local clock).
     * Preferred for sync to avoid clock skew.
     */
    suspend fun getPostsAfterLocalTime(timestamp: Long): List<Post> {
        return postDao.getPostsAfterLocalTime(timestamp)
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
    fun getPostCountForAuthor(authorId: String): Flow<Int> {
        return postDao.getPostCountForAuthor(authorId)
    }

    suspend fun getPostCount(): Int {
        return postDao.getPostCount()
    }
    
    fun getBatchStats(postIds: List<String>, userId: String): Flow<List<PostStatsEntry>> {
        return postDao.getBatchStats(postIds, userId)
    }

    /**
     * Update an existing post.
     */
    suspend fun updatePost(post: Post) {
        postDao.update(post)
    }

    companion object {
        const val PAGE_SIZE = 20
        const val PREFETCH_DISTANCE = 5
        const val INITIAL_LOAD_SIZE = 40
    }
}
