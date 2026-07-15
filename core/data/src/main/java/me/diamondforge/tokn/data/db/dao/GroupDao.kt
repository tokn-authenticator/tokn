package me.diamondforge.tokn.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.diamondforge.tokn.data.db.entity.GroupEntity

@Dao
interface GroupDao {

    @Query("SELECT * FROM groups ORDER BY sort_order ASC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups ORDER BY sort_order ASC")
    suspend fun getAllGroupsOnce(): List<GroupEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(group: GroupEntity): Long

    @Query("UPDATE groups SET color_argb = :colorArgb WHERE name = :name COLLATE NOCASE")
    suspend fun updateColor(name: String, colorArgb: Int?)

    @Query("UPDATE groups SET sort_order = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)

    @Query("UPDATE groups SET name = :to WHERE name = :from COLLATE NOCASE")
    suspend fun renameByName(from: String, to: String)

    @Query("DELETE FROM groups WHERE name = :name COLLATE NOCASE")
    suspend fun deleteByName(name: String)

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM groups")
    suspend fun maxSortOrder(): Int
}
