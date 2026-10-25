package dev.lyra.player.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsEngineTest {
    @Test
    fun parsesLineLyricsAndInterpolatesWordsByLength() {
        val lines = LrcParser.parse(
            """
            [00:01.00]I wonderful
            [00:05.00]Next line
            """.trimIndent(),
        )
        assertEquals(2, lines.size)
        assertEquals(1_000L, lines[0].startMs)
        assertEquals(5_000L, lines[0].endMs)
        assertEquals(listOf("I", "wonderful"), lines[0].words.map { it.text })
        assertEquals(1_000L, lines[0].words[0].startMs)
        assertEquals(5_000L, lines[0].words.last().endMs)
        val short = lines[0].words[0].endMs - lines[0].words[0].startMs
        val long = lines[0].words[1].endMs - lines[0].words[1].startMs
        assertTrue(long > short)
    }

    @Test
    fun parsesEnhancedWordTimestamps() {
        val lines = LrcParser.parse(
            """
            [00:10.00]<00:10.00>One <00:10.50>Two
            [00:12.00]Next
            """.trimIndent(),
        )
        val words = lines[0].words
        assertEquals(2, words.size)
        assertEquals(10_000L, words[0].startMs)
        assertEquals(10_500L, words[0].endMs)
        assertEquals(10_500L, words[1].startMs)
        assertEquals(12_000L, words[1].endMs)
    }

    @Test
    fun prefixBeforeFirstWordStampStartsAtTheLine() {
        val lines = LrcParser.parse("[00:01.00]Hello <00:01.40>there")
        assertEquals("Hello there", lines[0].text)
        assertEquals(1_000L, lines[0].words[0].startMs)
        assertEquals(1_400L, lines[0].words[0].endMs)
        assertEquals("there", lines[0].words[1].text)
        assertEquals(1_400L, lines[0].words[1].startMs)
        assertEquals(5_000L, lines[0].words[1].endMs)
    }

    @Test
    fun repeatedTimestampsBecomeSeparateLines() {
        val lines = LrcParser.parse("[00:01.00][00:08.00]Chorus")
        assertEquals(listOf(1_000L, 8_000L), lines.map { it.startMs })
        assertEquals("Chorus", lines[0].text)
        assertEquals("Chorus", lines[1].text)
        assertEquals(8_000L, lines[0].endMs)
    }

    @Test
    fun offsetShiftsLineAndWordTimes() {
        val lines = LrcParser.parse(
            """
            [offset:+100]
            [00:01.00]<00:01.20>Hi
            """.trimIndent(),
        )
        assertEquals(1_100L, lines[0].startMs)
        assertEquals(1_300L, lines[0].words[0].startMs)
    }

    @Test
    fun blankTimestampEndsThePreviousLine() {
        val lines = LrcParser.parse(
            """
            [00:01.00]Hello
            [00:02.00]
            [00:05.00]Next
            """.trimIndent(),
        )
        assertEquals(2, lines.size)
        assertEquals(2_000L, lines[0].endMs)
        assertEquals(5_000L, lines[1].startMs)
    }

    @Test
    fun ignoresMetadataAndGarbage() {
        val lines = LrcParser.parse(
            """
            [ar:Someone]
            [ti:Title]
            not a lyric
            [00:03.5]Café night
            """.trimIndent(),
        )
        assertEquals(1, lines.size)
        assertEquals(3_500L, lines[0].startMs)
        assertEquals(listOf("Café", "night"), lines[0].words.map { it.text })
    }

    @Test
    fun emptyAndMalformedInput() {
        assertTrue(LrcParser.parse("").isEmpty())
        assertTrue(LrcParser.parse("just words\n[bad]").isEmpty())
        assertTrue(LyricsSync.interpolate("   ", 0, 1000).isEmpty())
    }

    @Test
    fun lineIndexUsesTheLatestLineThatHasStarted() {
        val lines = LrcParser.parse(
            """
            [00:01.00]A
            [00:01.00]B
            [00:04.00]C
            """.trimIndent(),
        )
        assertEquals(-1, LyricsSync.lineIndex(lines, 500))
        assertEquals(-1, LyricsSync.lineIndex(emptyList(), 0))
        assertEquals(1, LyricsSync.lineIndex(lines, 1_000))
        assertEquals(1, LyricsSync.lineIndex(lines, 3_000))
        assertEquals(2, LyricsSync.lineIndex(lines, 9_000))
    }

    @Test
    fun wordProgressIsZeroAtTheStartAndFullAtTheEnd() {
        val line = LrcParser.parse("[00:00.00]<00:00.00>Alpha <00:01.00>Beta\n[00:02.00]Next").first()
        assertEquals(WordState(0, 0f), LyricsSync.wordState(line, 0))
        val mid = LyricsSync.wordState(line, 500)
        assertEquals(0, mid.index)
        assertEquals(0.5f, mid.progress, 0.02f)
        val done = LyricsSync.wordState(line, 2_000)
        assertEquals(1, done.index)
        assertEquals(1f, done.progress, 0.001f)
    }

    @Test
    fun interpolatedProgressDoesNotMoveBackwards() {
        val line = LrcParser.parse("[00:00.00]one two three\n[00:03.00]next").first()
        var lastIndex = 0
        var lastProgress = 0f
        var time = line.startMs
        while (time <= line.endMs) {
            val state = LyricsSync.wordState(line, time)
            assertTrue(state.index >= lastIndex)
            if (state.index == lastIndex) {
                assertTrue(state.progress + 0.001f >= lastProgress)
                lastProgress = state.progress
            } else {
                lastProgress = state.progress
            }
            lastIndex = state.index
            time += 40
        }
    }

    @Test
    fun centisecondAndColonFractions() {
        val lines = LrcParser.parse("[00:01:50]Half\n[01:02.5]Later")
        assertEquals(1_500L, lines[0].startMs)
        assertEquals(62_500L, lines[1].startMs)
    }
}
