package com.neo.data.repository

import com.neo.data.dao.SavedPostDao
import com.neo.data.model.SavedPost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedPostRepository @Inject constructor(
    private val savedPostDao: SavedPostDao
) {
    suspend fun toggle(postId: String) {
        val currentlySaved = savedPostDao.isSaved(postId).first()
        if (currentlySaved) {
            savedPostDao.delete(postId)
        } else {
            savedPostDao.insert(SavedPost(postId = postId))
        }
    }

    suspend fun save(postId: String) {
        savedPostDao.insert(SavedPost(postId = postId))
    }

    suspend fun unsave(postId: String) {
        savedPostDao.delete(postId)
    }

    suspend fun isSaved(postId: String): Boolean {
        return savedPostDao.isSaved(postId).first()
    }

    fun observeSavedPostIds(): Flow<Set<String>> {
        return savedPostDao.observeAllSavedPostIds().map { list -> list.toSet() }
    }

    fun observeIsSaved(postId: String): Flow<Boolean> {
        return savedPostDao.isSaved(postId)
    }

    suspend fun deleteAll() {
        savedPostDao.deleteAll()
    }
}
