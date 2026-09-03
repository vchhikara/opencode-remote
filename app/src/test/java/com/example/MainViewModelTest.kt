package com.example

import org.junit.Test
import org.junit.Assert.*

class MainViewModelTest {
    @Test
    fun testInitialization() {
        val viewModel = MainViewModel()
        assertNotNull(viewModel)
        assertEquals("Idle", viewModel.agentState.value)
    }
}
