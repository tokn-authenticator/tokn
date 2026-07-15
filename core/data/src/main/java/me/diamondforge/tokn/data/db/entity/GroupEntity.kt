package me.diamondforge.tokn.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import me.diamondforge.tokn.domain.model.Group

@Entity(tableName = "groups", indices = [Index(value = ["name"], unique = true)])
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    @ColumnInfo(name = "color_argb") val colorArgb: Int? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)

fun GroupEntity.toDomain(): Group = Group(
    id = id,
    name = name,
    colorArgb = colorArgb,
    sortOrder = sortOrder,
)

fun Group.toEntity(): GroupEntity = GroupEntity(
    id = id,
    name = name,
    colorArgb = colorArgb,
    sortOrder = sortOrder,
)
