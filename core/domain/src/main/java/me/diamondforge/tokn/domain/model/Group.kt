package me.diamondforge.tokn.domain.model

data class Group(
    val id: Long = 0,
    val name: String,
    val colorArgb: Int? = null,
    val sortOrder: Int = 0,
)
