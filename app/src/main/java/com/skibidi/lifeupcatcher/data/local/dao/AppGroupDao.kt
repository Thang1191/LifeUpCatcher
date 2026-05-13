package com.skibidi.lifeupcatcher.data.local.dao

import androidx.room.*
import com.skibidi.lifeupcatcher.data.local.entity.AppGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppGroupDao {
    @Query("SELECT * FROM app_groups")
    fun getAllGroups(): Flow<List<AppGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: AppGroupEntity)

    @Update
    suspend fun updateGroup(group: AppGroupEntity)

    @Delete
    suspend fun deleteGroup(group: AppGroupEntity)

    @Query("SELECT * FROM app_groups WHERE id = :id")
    suspend fun getGroupById(id: String): AppGroupEntity?
}
