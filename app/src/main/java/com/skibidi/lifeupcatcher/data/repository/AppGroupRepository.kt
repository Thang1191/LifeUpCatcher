package com.skibidi.lifeupcatcher.data.repository

import com.skibidi.lifeupcatcher.data.local.dao.AppGroupDao
import com.skibidi.lifeupcatcher.data.local.entity.AppGroupEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppGroupRepository @Inject constructor(
    private val appGroupDao: AppGroupDao
) {
    val allGroups: Flow<List<AppGroupEntity>> = appGroupDao.getAllGroups()

    suspend fun addGroup(group: AppGroupEntity) {
        appGroupDao.insertGroup(group)
    }

    suspend fun updateGroup(group: AppGroupEntity) {
        appGroupDao.updateGroup(group)
    }

    suspend fun deleteGroup(group: AppGroupEntity) {
        appGroupDao.deleteGroup(group)
    }

    suspend fun getGroupById(id: String): AppGroupEntity? {
        return appGroupDao.getGroupById(id)
    }
}
