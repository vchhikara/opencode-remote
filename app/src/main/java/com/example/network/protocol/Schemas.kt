package com.example.network.protocol

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceDto(
    val id: String,
    val name: String,
    val path: String
)

@Serializable
data class FileNodeDto(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val children: List<FileNodeDto>? = null
)

@Serializable
data class ChatMessageDto(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val actionDescription: String? = null,
    val hasDetails: Boolean = false
)

@Serializable
data class DiffPatchDto(
    val fileName: String,
    val diffText: String
)

@Serializable
data class TaskProcessDto(
    val id: String,
    val name: String,
    val port: String? = null,
    val status: String
)

@Serializable
data class GitStatusDto(
    val branch: String,
    val modifiedFiles: List<String>,
    val addedFiles: List<String>,
    val deletedFiles: List<String>,
    val canPush: Boolean,
    val canPull: Boolean
)
