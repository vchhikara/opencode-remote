package com.opencode.remote.ui

import com.opencode.remote.ui.screens.diff.DiffLineType
import com.opencode.remote.ui.screens.diff.parseDiff
import org.junit.Assert.assertEquals
import org.junit.Test

class DiffParsingTest {

    @Test
    fun testParseDiff() {
        val patch = """
            --- a/file.txt
            +++ b/file.txt
            @@ -1,3 +1,4 @@
             context line 1
            -deleted line
            +added line 1
            +added line 2
             context line 2
        """.trimIndent()

        val parsed = parseDiff(patch)

        assertEquals(2, parsed.additions)
        assertEquals(1, parsed.deletions)
        
        assertEquals(DiffLineType.HEADER, parsed.lines[0].type)
        assertEquals(DiffLineType.HEADER, parsed.lines[1].type)
        assertEquals(DiffLineType.HEADER, parsed.lines[2].type)
        assertEquals(DiffLineType.CONTEXT, parsed.lines[3].type)
        assertEquals(DiffLineType.DELETION, parsed.lines[4].type)
        assertEquals(DiffLineType.ADDITION, parsed.lines[5].type)
        assertEquals(DiffLineType.ADDITION, parsed.lines[6].type)
        assertEquals(DiffLineType.CONTEXT, parsed.lines[7].type)
    }
    
    @Test
    fun testParseEmptyDiff() {
        val parsed = parseDiff(null)
        assertEquals(0, parsed.additions)
        assertEquals(0, parsed.deletions)
        assertEquals(0, parsed.lines.size)
    }
}
