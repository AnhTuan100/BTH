package com.example.baith2.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val email: String = "",
    // ID của liên hệ tương ứng trong Danh bạ hệ thống (null = chưa đồng bộ)
    val systemContactId: Long? = null
)
