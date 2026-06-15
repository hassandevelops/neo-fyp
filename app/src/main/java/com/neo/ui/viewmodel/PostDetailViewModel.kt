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
import com.neo.data.model.PeerProfile
import com.neo.data.repository.PostRepository
import com.neo.data.repository.PeerProfileRepository
import com.neo.data.repository.ReactionRepository
import com.neo.data.repository.SavedPostRepository
import com.neo.domain.usecase.CreateCommentUseCase
import com.neo.domain.usecase.CreateReactionUseCase
import com.neo.domain.usecase.DeleteReactionUseCase
import com.neo.security.CryptoManager
import com.neo.security.IdentityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
    private val identityManager: IdentityManager,
    private val peerProfileRepository: PeerProfileRepository,
    private val notificationRepository: NotificationRepository,
    private val savedPostRepository: SavedPostRepository
) : AndroidViewModel(application) {

    private val postId: String? = savedStateHandle.get<String>("postId")

    /** The local user's identity DID + deviceId — ids that represent "me". */
    val myDid: String = runCatching { identityManager.getDid() }.getOrDefault("")
    val selfIds: Set<String> =
        setOf(myDid, cryptoManager.getDeviceId()).filter { it.isNotEmpty() }.toSet()

    /** Peer profiles keyed by DID, for resolving avatars of other users. */
    val profilesByDid: StateFlow<Map<String, PeerProfile>> =
        peerProfileRepository.observeProfilesByDid()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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
        if (postIds.isEmpty()) return flowOf(emptyMap())
        return postRepository.getBatchStats(postIds, cryptoManager.getDeviceId())
            .map { entries ->
                entries.associate { entry ->
                    entry.postId to PostStats(
                        commentCount = entry.commentCount,
                        likeCount = entry.likeCount,
                        hasLiked = entry.hasLiked
                    )
                }
            }
    }

    fun getTopLevelCommentsForPost(postId: String) =
        commentRepository.getTopLevelCommentsForPost(postId)

    fun getRepliesForComment(commentId: String) =
        commentRepository.getRepliesForComment(commentId)

    /** Device id of the local user — used to resolve the local user's own avatar. */
    fun currentUserId(): String = cryptoManager.getDeviceId()

    /**
     * Build a flattened two-level thread structure (top-level comment + its
     * replies) from a single stream of all comments for the post. Replies are
     * grouped under their thread root, so a reply-to-a-reply still appears under
     * the original top-level comment instead of nesting indefinitely.
     */
    fun getCommentThreadsForPost(postId: String): Flow<List<com.neo.data.model.CommentThread>> =
        commentRepository.getAllCommentsForPost(postId).map { comments ->
            val byId = comments.associateBy { it.id }

            // Walk parent links up to the first comment with no parent. If a
            // parent is missing locally, the deepest reachable comment becomes
            // the root so nothing is dropped from the UI.
            fun rootIdOf(comment: com.neo.data.model.Comment): String {
                var current = comment
                val guard = HashSet<String>()
                while (current.parentCommentId != null && guard.add(current.id)) {
                    val parent = byId[current.parentCommentId] ?: break
                    current = parent
                }
                return current.id
            }

            val repliesByRoot = HashMap<String, MutableList<com.neo.data.model.Comment>>()
            val topLevel = ArrayList<com.neo.data.model.Comment>()

            for (comment in comments) {
                if (comment.parentCommentId == null) {
                    topLevel.add(comment)
                } else {
                    val root = rootIdOf(comment)
                    if (root == comment.id) {
                        // Orphaned reply (root not found locally) — surface it as top-level.
                        topLevel.add(comment)
                    } else {
                        repliesByRoot.getOrPut(root) { mutableListOf() }.add(comment)
                    }
                }
            }

            topLevel
                .sortedByDescending { it.timestamp } // newest threads first
                .map { root ->
                    com.neo.data.model.CommentThread(
                        comment = root,
                        replies = (repliesByRoot[root.id] ?: emptyList())
                            .sortedBy { it.timestamp } // oldest reply first within a thread
                    )
                }
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
                onSuccess = { },
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