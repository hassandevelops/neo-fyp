package com.neo.domain.usecase

import androidx.paging.PagingData
import com.neo.data.model.Post
import com.neo.data.repository.PostRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for retrieving the feed of posts.
 */
class GetFeedUseCase @Inject constructor(
    private val postRepository: PostRepository
) {
    
    /**
     * Get all posts ordered by timestamp (newest first).
     * Returns a Flow for reactive updates.
     */
    fun execute(): Flow<List<Post>> {
        return postRepository.getAllPosts()
    }

    /**
     * Get paginated posts for infinite scrolling.
     * Returns a PagingData flow.
     */
    fun executePaged(): Flow<PagingData<Post>> {
        return postRepository.getPostsPaged()
    }
}