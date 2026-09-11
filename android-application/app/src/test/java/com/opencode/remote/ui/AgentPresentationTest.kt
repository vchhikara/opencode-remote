package com.opencode.remote.ui

import com.opencode.remote.data.dto.GitStatusDto
import com.opencode.remote.data.dto.PermissionRequestDto
import com.opencode.remote.data.dto.QuestionRequestDto
import com.opencode.remote.data.dto.StreamingMessageDto
import com.opencode.remote.data.dto.ToolStep
import com.opencode.remote.ui.state.AgentPhase
import com.opencode.remote.ui.state.AgentPresentation
import com.opencode.remote.ui.state.RepoFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The status card's categories are *derived* from the bridge's free-text state; these
 *  pin down that arbitrary strings still map somewhere sensible and that decisions win. */
class AgentPresentationTest {

    @Test
    fun idleVariantsAreIdle() {
        for (raw in listOf("Idle", "idle", "  IDLE  ", "", "ready", "done")) {
            assertEquals(raw, AgentPhase.Idle, AgentPresentation.phaseOf(raw, null, null, null))
        }
    }

    @Test
    fun thinkingAndUnknownStatesAreActive() {
        assertEquals(AgentPhase.Thinking, AgentPresentation.phaseOf("Thinking...", null, null, null))
        // A state string nobody anticipated must not be rejected or shown as idle.
        assertEquals(AgentPhase.Working, AgentPresentation.phaseOf("Compacting context", null, null, null))
        assertEquals(AgentPhase.Error, AgentPresentation.phaseOf("Error: model timeout", null, null, null))
    }

    @Test
    fun pendingPermissionOrQuestionAlwaysMeansWaiting() {
        val perm = PermissionRequestDto("p1", tool = "bash")
        assertEquals(AgentPhase.Waiting, AgentPresentation.phaseOf("Idle", null, perm, null))
        assertEquals(AgentPhase.Waiting, AgentPresentation.phaseOf("Thinking...", StreamingMessageDto("x"), null, QuestionRequestDto("q1")))
    }

    @Test
    fun streamingMeansWorkingOnceContentArrives() {
        assertEquals(AgentPhase.Thinking, AgentPresentation.phaseOf("Idle", StreamingMessageDto(), null, null))
        assertEquals(AgentPhase.Working, AgentPresentation.phaseOf("Idle", StreamingMessageDto(text = "Hel"), null, null))
        assertEquals(AgentPhase.Working, AgentPresentation.phaseOf("Idle", StreamingMessageDto(runningTool = "read"), null, null))
    }

    @Test
    fun workingCopyNamesTheRunningToolAndItsTarget() {
        val streaming = StreamingMessageDto(runningTool = "read", toolSteps = listOf(ToolStep("read", "src/App.kt")))
        val copy = AgentPresentation.statusCopy(AgentPhase.Working, "Running", streaming, null, null, RepoFacts(null, null, 0, null), 0L)
        assertEquals("Working", copy.eyebrow)
        assertEquals("Running read", copy.title)
        assertEquals("src/App.kt", copy.detail)
    }

    @Test
    fun unknownStateIsShownVerbatimAsTitle() {
        val copy = AgentPresentation.statusCopy(AgentPhase.Working, "compacting context", null, null, null, RepoFacts(null, null, 0, null), 0L)
        assertEquals("Compacting context", copy.title)
    }

    @Test
    fun waitingCopyUsesRealPermissionTool() {
        val copy = AgentPresentation.statusCopy(
            AgentPhase.Waiting, "Idle", null, PermissionRequestDto("p1", tool = "edit"), null, RepoFacts(null, null, 0, null), 0L
        )
        assertEquals("Waiting on you", copy.eyebrow)
        assertTrue(copy.detail.contains("edit"))
    }

    @Test
    fun idleDetailOnlyStatesKnownFacts() {
        val none = AgentPresentation.idleDetail(RepoFacts(null, null, 0, null), 0L)
        assertEquals("Send a prompt to start a run.", none)
        val some = AgentPresentation.idleDetail(RepoFacts("main", 3, 2, null), 0L)
        assertEquals("3 files uncommitted on main. 2 diffs waiting for review.", some)
    }

    @Test
    fun repoLineComesFromGitStatus() {
        assertEquals("dev · 2 uncommitted", AgentPresentation.repoLine(GitStatusDto(branch = "dev", modifiedFiles = listOf("a"), addedFiles = listOf("b")), null))
        assertEquals("dev · clean", AgentPresentation.repoLine(GitStatusDto(branch = "dev"), null))
        assertEquals("project", AgentPresentation.repoLine(null, "/home/me/project/"))
        assertEquals("No workspace open", AgentPresentation.repoLine(null, null))
    }
}
