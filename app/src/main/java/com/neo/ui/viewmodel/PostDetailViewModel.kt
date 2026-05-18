package com.neo.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.neo.data.model.Notification
import com.neo.data.model.Post
import com.neo.data.model.ReactionType
import com.neo.data.repository.CommentRepository
import com.neo.data.repository.NotificationRepository
import com.neo.data.repository.PostRepository
import com.neo.data.repository.ReactionRepository
import com.neo.data.repository.SavedPostRepository
import com.neo.domain.usecase.CreateCommentUseCase
import com.neo.domain.usecase.CreateReactionUseCase
import com.neo.domain.usecase.DeleteReactionUseCase
import com.neo.security.CryptoManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PostStats(
    val commentCount: Int = 0,
    val likeCount: Int = 0,
    val hasLiked: Boolean = false
)

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    application: Application,
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val createCommentUseCase: CreateCommentUseCase,
    private val reactionRepository: ReactionRepository,
    private val createReactionUseCase: CreateReactionUseCase,
    private val deleteReactionUseCase: DeleteReactionUseCase,
    private val cryptoManager: CryptoManager,
    private val notificationRepository: NotificationRepository,
    private val savedPostRepository: SavedPostRepository
) : AndroidViewModel(application) {

    private val postId: String? = savedStateHandle.get<String>("postId")

    var loadAttempted = false
        private set

    val post: StateFlow<Post?> = flow {
        if (postId != null) {
            emit(postRepository.getPostById(postId))
        } else {
            emit(null)
        }
    }.onEach { loadAttempted = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Error(val message: String) : UiState()
        data class Success(val message: String) : UiState()
    }

    fun getPostStatsMapFlow(postIds: List<String>): Flow<Map<String, PostStats>> {
        if (postIds.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyMap())
        val flows = postIds.map { postId ->
            combine(
                getCommentCountForPost(postId),
                getLikeCountForPost(postId),
                hasUserLikedPostFlow(postId)
            ) { commentCount, likeCount, hasLiked ->
                postId to PostStats(commentCount, likeCount, hasLiked)
            }
        }
        return combine(flows) { pairs -> pairs.toMap() }
    }

    fun getTopLevelCommentsForPost(postId: String) =
        commentRepository.getTopLevelCommentsForPost(postId)

    fun getRepliesForComment(commentId: String) =
        commentRepository.getRepliesForComment(commentId)

    fun getCommentCountForPost(postId: String) =
        commentRepository.getCommentCountForPostFlow(postId)

    fun getLikeCountForPost(postId: String) =
        reactionRepository.getReactionCountForPost(postId, ReactionType.LIKE)

    fun hasUserLikedPost(postId: String): Boolean {
        return false // Must be checked via Flow
    }

    fun hasUserLikedPostFlow(postId: String) =
        reactionRepository.hasUserReactedFlow(postId, cryptoManager.getDeviceId(), ReactionType.LIKE)

    fun createComment(
        postId: String,
        content: String,
        authorName: String,
        parentCommentId: String? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            val result = createCommentUseCase(
                postId = postId,
                content = content,
                authorName = authorName,
                parentCommentId = parentCommentId
            )

            result.fold(
                onSuccess = {
                    _uiState.value = UiState.Success("Comment created")
                    onSuccess()
                    notificationRepository.insert(
                        Notification(
                            id = UUID.randomUUID().toString(),
                            type = "comment",
                            message = "You commented on a post",
                            postId = postId,
                            authorName = authorName,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "Failed to create comment")
                    onError(error.message ?: "Failed to create comment")
                }
            )
        }
    }

    fun toggleLike(
        postId: String,
        userName: String,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val userId = cryptoManager.getDeviceId()
            val hasLiked = reactionRepository.hasUserReacted(postId, userId, ReactionType.LIKE)

            val result = if (hasLiked) {
                deleteReactionUseCase(postId, ReactionType.LIKE)
            } else {
                createReactionUseCase(postId, userName, ReactionType.LIKE)
            }

            result.fold(
                onSuccess = {
                    if (!hasLiked) {
                        notificationRepository.insert(
                            Notification(
                                id = UUID.randomUUID().toString(),
                                type = "like",
                                message = "$userName liked a post",
                                postId = postId,
                                authorName = userName,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                },
                onFailure = { error ->
                    onError(error.message ?: "Failed to update like")
                }
            )
        }
    }

    fun toggleSave(postId: String) {
        viewModelScope.launch {
            savedPostRepository.toggle(postId)
        }
    }

    fun observeIsSaved(postId: String): Flow<Boolean> {
        return savedPostRepository.observeIsSaved(postId)
    }

    fun resetUiState() {
        _uiState.value = UiState.Idle
    }
}