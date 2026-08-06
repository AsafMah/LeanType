package helium314.keyboard.latin.utils

import helium314.keyboard.latin.common.splitOnWhitespace
import org.junit.Assert.assertEquals
import org.junit.Test

class SpacedTokensTest {

    @Test
    fun `empty string returns empty list`() {
        val tokens = "".splitOnWhitespace()
        assertEquals(0, tokens.size)
    }

    @Test
    fun `string with only spaces returns empty list`() {
        val tokens = "   ".splitOnWhitespace()
        assertEquals(0, tokens.size)
    }

    @Test
    fun `string with one token without spaces returns one token`() {
        val tokens = "word".splitOnWhitespace()
        assertEquals(listOf("word"), tokens)
    }

    @Test
    fun `string with multiple tokens separated by single spaces returns tokens`() {
        val tokens = "this is a test".splitOnWhitespace()
        assertEquals(listOf("this", "is", "a", "test"), tokens)
    }

    @Test
    fun `string with multiple tokens separated by multiple spaces returns tokens`() {
        val tokens = "this  is   a    test".splitOnWhitespace()
        assertEquals(listOf("this", "is", "a", "test"), tokens)
    }

    @Test
    fun `string with leading spaces returns tokens`() {
        val tokens = "  leading".splitOnWhitespace()
        assertEquals(listOf("leading"), tokens)
    }

    @Test
    fun `string with trailing spaces returns tokens`() {
        val tokens = "trailing  ".splitOnWhitespace()
        assertEquals(listOf("trailing"), tokens)
    }

    @Test
    fun `string with leading and trailing spaces returns tokens`() {
        val tokens = "   both   ".splitOnWhitespace()
        assertEquals(listOf("both"), tokens)
    }

    @Test
    fun `string with different types of whitespace returns tokens`() {
        val tokens = "token1\ttoken2\ntoken3\rtoken4".splitOnWhitespace()
        assertEquals(listOf("token1", "token2", "token3", "token4"), tokens)
    }

    @Test
    fun `string with punctuations as tokens returns tokens`() {
        val tokens = "word1, word2!".splitOnWhitespace()
        assertEquals(listOf("word1,", "word2!"), tokens)
    }

    @Test
    fun `string with emojis as tokens returns tokens`() {
        val tokens = "hello 🌍!".splitOnWhitespace()
        assertEquals(listOf("hello", "🌍!"), tokens)
    }

}
