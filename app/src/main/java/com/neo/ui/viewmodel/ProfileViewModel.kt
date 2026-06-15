package com.neo.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.neo.data.model.Post
import com.neo.data.model.PeerProfile
import com.neo.data.preferences.UserPreferences
import com.neo.data.repository.PostRepository
import com.neo.data.repository.PeerProfileRepository
import com.neo.data.repository.FollowRepository
import com.neo.data.repository.SavedPostRepository
import com.neo.domain.port.ISyncPort
import com.neo.security.CryptoManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
    private val cryptoManager: CryptoManager,
    private val postRepository: PostRepository,
    private val savedPostRepository: SavedPostRepository,
    private val peerProfileRepository: PeerProfileRepository,
    private val followRepository: FollowRepository,
    private val toggleFollowUseCase: com.neo.domain.usecase.ToggleFollowUseCase,
    private val syncPort: ISyncPort,
    private val identityManager: com.neo.security.IdentityManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** The local user's identity DID (follows are keyed by DID, not deviceId). */
    private val myDid: String = runCatching { identityManager.getDid() }.getOrDefault("")

    // When a "userId" nav arg is present and differs from this device, the screen
    // shows another user's (read-only) profile; otherwise it's the current user.
    private val targetUserId: String? = savedStateHandle.get<String>("userId")
    private val effectiveAuthorId: String = targetUserId ?: cryptoManager.getDeviceId()
    val isCurrentUserProfile: Boolean =
        targetUserId == null || targetUserId == cryptoManager.getDeviceId()

    // Posts are stored with authorId = did:key (see CreatePostUseCase), NOT the
    // legacy deviceId. So the local user's "own" posts must be matched by their DID
    // (plus deviceId as a fallback for any legacy/local rows). For another user's
    // profile, their nav-arg id is already their DID, so match that directly.
    private val authorIds: List<String> = if (isCurrentUserProfile) {
        val did = runCatching { identityManager.getDid() }.getOrNull()
        listOfNotNull(did, cryptoManager.getDeviceId()).distinct()
    } else {
        listOf(effectiveAuthorId)
    }

    // For another user, their profile metadata (name/bio/avatar) arrives via
    // profile-sync gossip, keyed by their DID (== effectiveAuthorId here).
    private val peerProfile: StateFlow<PeerProfile?> = if (isCurrentUserProfile) {
        MutableStateFlow(null)
    } else {
        peerProfileRepository.observe(effectiveAuthorId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    private val _profileName = MutableStateFlow(userPreferences.userName)

    // For self → editable prefs name; for another user → synced profile name,
    // falling back to the name denormalized on their posts, then a placeholder.
    val profileName: StateFlow<String> = if (isCurrentUserProfile) {
        _profileName.asStateFlow()
    } else {
        combine(peerProfile, postRepository.getPostsByAuthors(authorIds)) { profile, posts ->
            profile?.displayName?.takeIf { it.isNotBlank() }
                ?: posts.firstOrNull()?.authorName
                ?: "Neo User"
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Neo User")
    }

    private val _profileBio = MutableStateFlow(userPreferences.userBio)
    val profileBio: StateFlow<String> = if (isCurrentUserProfile) {
        _profileBio.asStateFlow()
    } else {
        peerProfile.map { it?.bio ?: "" }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    }

    private val _profileImageUri = MutableStateFlow(userPreferences.profileImageUri)
    val profileImageUri: StateFlow<String?> = if (isCurrentUserProfile) {
        _profileImageUri.asStateFlow()
    } else {
        peerProfile.map { it?.avatarPath }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    val deviceId: String
        get() = cryptoManager.getDeviceId()

    /** The local user's display name — used when reacting to posts from the profile feed. */
    val currentUserName: String get() = userPreferences.userName

    val handle: StateFlow<String> = profileName.map { name ->
        "@${name.lowercase().replace("\\s+".toRegex(), "")}_${effectiveAuthorId.takeLast(4)}"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "@unknown")

    val postCount: StateFlow<Int> = postRepository.getPostCountForAuthors(authorIds)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val connectedPeersCount: StateFlow<Int> = syncPort.connectedPeersCount

    // DID of the profile being viewed (self → my DID; other → their DID/nav arg).
    private val profileDid: String = if (isCurrentUserProfile) myDid else effectiveAuthorId

    val followerCount: StateFlow<Int> = followRepository.observeFollowerCount(profileDid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val followingCount: StateFlow<Int> = followRepository.observeFollowingCount(profileDid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Whether the local user currently follows the viewed (other) user. */
    val isFollowing: StateFlow<Boolean> =
        if (isCurrentUserProfile || myDid.isEmpty()) {
            MutableStateFlow(false)
        } else {
            followRepository.observeIsFollowing(myDid, profileDid)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
        }

    private val _isFollowLoading = MutableStateFlow(false)
    val isFollowLoading: StateFlow<Boolean> = _isFollowLoading.asStateFlow()

    /** Follow/unfollow the viewed user, propagating the change across the mesh. */
    fun toggleFollow() {
        if (isCurrentUserProfile) return
        viewModelScope.launch {
            _isFollowLoading.value = true
            try {
                val currentlyFollowing = isFollowing.value
                toggleFollowUseCase(profileDid, !currentlyFollowing)
            } finally {
                _isFollowLoading.value = false
            }
        }
    }

    val userPosts: StateFlow<List<Post>> = postRepository.getPostsByAuthors(authorIds)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedPostIds: StateFlow<Set<String>> = savedPostRepository.observeSavedPostIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val savedPosts: StateFlow<List<Post>> = combine(
        savedPostRepository.observeSavedPostIds(),
        postRepository.getAllPosts()
    ) { ids, allPosts ->
        allPosts.filter { it.id in ids }
            .let { filtered ->
                val order = ids.withIndex().associate { (index, id) -> id to index }
                filtered.sortedBy { order[it.id] ?: Int.MAX_VALUE }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    sealed class UiState {
        object Idle : UiState()
        object Saving : UiState()
        object Saved : UiState()
        data class Error(val message: String) : UiState()
    }

    private fun saveImageToInternalStorage(uriStr: String?): String? {
        if (uriStr == null) return null
        val uri = Uri.parse(uriStr)
        
        if (uri.scheme == "file" && uri.path?.contains(context.filesDir.path) == true) {
            return uriStr
        }
        
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                context.filesDir.listFiles { _, name -> name.startsWith("profile_picture_") }?.forEach { 
                    it.delete()
                }
                
                val file = File(context.filesDir, "profile_picture_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                Uri.fromFile(file).toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun updateProfile(name: String, bio: String, imageUri: String? = null) {
        viewModelScope.launch {
            _uiState.value = UiState.Saving
            try {
                val savedImageUri = if (imageUri != null) {
                    saveImageToInternalStorage(imageUri)
                } else {
                    null
                }
                userPreferences.userName = name
                userPreferences.userBio = bio
                userPreferences.profileImageUri = savedImageUri
                userPreferences.profileUpdatedAt = System.currentTimeMillis()
                _profileName.value = name
                _profileBio.value = bio
                _profileImageUri.value = savedImageUri
                // Propagate the updated profile (name/bio/avatar) across the mesh.
                runCatching { syncPort.broadcastProfile() }
                _uiState.value = UiState.Saved
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to save profile")
            }
        }
    }

    fun toggleSave(postId: String) {
        viewModelScope.launch {
            savedPostRepository.toggle(postId)
        }
    }

    fun resetUiState() {
        _uiState.value = UiState.Idle
    }
}
