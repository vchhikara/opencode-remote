package com.example.network

data class GitStatus(
    val branch: String,
    val modifiedFiles: List<String>,
    val addedFiles: List<String>,
    val deletedFiles: List<String>,
    val canPush: Boolean,
    val canPull: Boolean
)
