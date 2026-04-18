package com.anzupop.saki.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val username: String,
    val password: String,
    val clientName: String,
    val apiVersion: String,
    val createdAt: Long,
    val updatedAt: Long,
)
