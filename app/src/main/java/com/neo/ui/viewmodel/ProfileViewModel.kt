package com.neo.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.data.model.Post
import com.neo.data.preferences.UserPreferences
import com.neo.data.repository.PostRepository
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
    private val syncPort: ISyncPort
) : ViewModel() {

    private val _profileName = MutableStateFlow(userPreferences.userName)
    val profileName: StateFlow<String> = _profileName.asStateFlow()

    private val _profileBio = MutableStateFlow(userPreferences.userBio)
    val profileBio: StateFlow<String> = _profileBio.asStateFlow()

    private val _profileImageUri = MutableStateFlow(userPreferences.profileImageUri)
    val profileImageUri: StateFlow<String?> = _profileImageUri.asStateFlow()

    val deviceId: String
        get() = cryptoManager.getDeviceId()

    private val uid = cryptoManager.getDeviceId()
    val handle: StateFlow<String> = _profileName.map { name ->
        "@${name.lowercase().replace("\\s+".toRegex(), "")}_${uid.takeLast(4)}"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "@unknown")

    val postCount: StateFlow<Int> = postRepository.getPostCountForAuthor(deviceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val connectedPeersCount: StateFlow<Int> = syncPort.connectedPeersCount

    val userPosts: StateFlow<List<Post>> = postRepository.getPostsByAuthor(deviceId)
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
                _profileName.value = name
                _profileBio.value = bio
                _profileImageUri.value = savedImageUri
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
