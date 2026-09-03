package com.example.network

import kotlinx.serialization.Serializable

@Serializable
data class Workspace(
    val id: String,
    val name: String,
    val path: String
)

@Serializable
data class Project(
    val id: String,
    val name: String,
    val path: String,
    val workspaceId: String
)

@Serializable
data class ConnectionRequest(
    val publicKey: String,
    val deviceName: String
)

@Serializable
data class ConnectionResponse(
    val success: Boolean,
    val sessionId: String?,
    val error: String?
)
