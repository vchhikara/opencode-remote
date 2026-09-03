package com.example.network

import kotlinx.serialization.Serializable

@Serializable
data class FileNode(
    val id: String,
    val name: String,
    val isFolder: Boolean,
    val size: String = "",
    val path: String = "",
    val children: List<FileNode>? = null,
    val isLoaded: Boolean = false
)
