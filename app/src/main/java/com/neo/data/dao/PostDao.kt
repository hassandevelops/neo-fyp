package com.neo.data.dao

import androidx.paging.PagingSource
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

    @Query("SELECT * FROM posts WHERE authorId = :authorId ORDER BY timestamp DESC")
    fun getPostsByAuthor(authorId: String): Flow<List<Post>>

    /**
     * Get all posts once for export and maintenance tasks.
     */
    @Query("SELECT * FROM posts ORDER BY timestamp DESC")
    suspend fun getAllPostsList(): List<Post>

    /**
     * Get all image hashes currently referenced by posts.
     */
    @Query("SELECT imageHash FROM posts WHERE imageHash IS NOT NULL")
    suspend fun getReferencedImageHashes(): List<String>

    /**
     * Get paginated posts for infinite scrolling feed.
     * Returns posts ordered by timestamp (newest first).
     */
    @Query("SELECT * FROM posts ORDER BY timestamp DESC")
    fun getPostsPaged(): PagingSource<Int, Post>

    /**
     * Get paginated posts with a limit.
     * @param limit Maximum number of posts to return
     * @param offset Number of posts to skip
     */
    @Query("SELECT * FROM posts ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getPostsPaginated(limit: Int, offset: Int): List<Post>
    
    /**
     * Get posts created after a specific timestamp, using the author's clock.
     * Kept for reference / legacy usage. For sync, prefer getPostsAfterLocalTime.
     */
    @Query("SELECT * FROM posts WHERE timestamp > :afterTimestamp ORDER BY timestamp ASC")
    suspend fun getPostsAfter(afterTimestamp: Long): List<Post>

    /**
     * Get posts first received after a specific timestamp (local clock).
     * Used for sync operations to avoid clock skew issues.
     */
    @Query("SELECT * FROM posts WHERE firstSeenTimestamp > :afterTimestamp ORDER BY firstSeenTimestamp ASC LIMIT 200")
    suspend fun getPostsAfterLocalTime(afterTimestamp: Long): List<Post>
    
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
    @Query("SELECT COUNT(*) FROM posts WHERE authorId = :authorId")
    fun getPostCountForAuthor(authorId: String): Flow<Int>

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

    @Query("""
        SELECT p.id AS postId,
            (SELECT COUNT(*) FROM comments c WHERE c.postId = p.id) AS commentCount,
            (SELECT COUNT(*) FROM reactions r WHERE r.postId = p.id AND r.type = 'LIKE') AS likeCount,
            EXISTS(SELECT 1 FROM reactions r2 WHERE r2.postId = p.id AND r2.userId = :userId AND r2.type = 'LIKE') AS hasLiked
        FROM posts p
        WHERE p.id IN (:postIds)
    """)
    fun getBatchStats(postIds: List<String>, userId: String): Flow<List<PostStatsEntry>>
}

data class PostStatsEntry(
    val postId: String,
    val commentCount: Int,
    val likeCount: Int,
    val hasLiked: Boolean
)
