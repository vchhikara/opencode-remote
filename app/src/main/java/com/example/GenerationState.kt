package com.example

sealed interface GenerationState {
    object Idle : GenerationState
    data class Generating(val progressText: String, val logs: List<String>) : GenerationState
    data class Success(val repoUrl: String, val fileTreeJson: String) : GenerationState
    data class Error(val message: String) : GenerationState
}
