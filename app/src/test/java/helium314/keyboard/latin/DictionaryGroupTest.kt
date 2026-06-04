package helium314.keyboard.latin

import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.ExpandableBinaryDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.never
import org.mockito.Mockito
import java.util.Locale

class DictionaryGroupTest {

    @Test
    fun testCloseDict_MainDict() {
        val dictGroupClass = Class.forName("helium314.keyboard.latin.DictionaryGroup")
        val constructor = dictGroupClass.declaredConstructors.first { it.parameterCount == 4 }
        constructor.isAccessible = true

        val mockMainDict = mock(Dictionary::class.java)
        val instance = constructor.newInstance(Locale.ENGLISH, mockMainDict, emptyMap<String, ExpandableBinaryDictionary>(), null)

        val closeDictMethod = dictGroupClass.getDeclaredMethod("closeDict", String::class.java)
        closeDictMethod.isAccessible = true

        closeDictMethod.invoke(instance, Dictionary.TYPE_MAIN)

        verify(mockMainDict).close()
    }

    @Test
    fun testCloseDict_SubDict() {
        val dictGroupClass = Class.forName("helium314.keyboard.latin.DictionaryGroup")
        val constructor = dictGroupClass.declaredConstructors.first { it.parameterCount == 4 }
        constructor.isAccessible = true

        val mockSubDict = mock(ExpandableBinaryDictionary::class.java)
        val subDictsMap = mapOf(Dictionary.TYPE_USER_HISTORY to mockSubDict)

        val instance = constructor.newInstance(Locale.ENGLISH, null, subDictsMap, null)

        val closeDictMethod = dictGroupClass.getDeclaredMethod("closeDict", String::class.java)
        closeDictMethod.isAccessible = true

        val getSubDictMethod = dictGroupClass.getDeclaredMethod("getSubDict", String::class.java)
        getSubDictMethod.isAccessible = true

        assertEquals(mockSubDict, getSubDictMethod.invoke(instance, Dictionary.TYPE_USER_HISTORY))

        closeDictMethod.invoke(instance, Dictionary.TYPE_USER_HISTORY)

        verify(mockSubDict).close()
        assertNull(getSubDictMethod.invoke(instance, Dictionary.TYPE_USER_HISTORY))
    }

    @Test
    fun testCloseDict_MissingDict() {
        val dictGroupClass = Class.forName("helium314.keyboard.latin.DictionaryGroup")
        val constructor = dictGroupClass.declaredConstructors.first { it.parameterCount == 4 }
        constructor.isAccessible = true

        val instance = constructor.newInstance(Locale.ENGLISH, null, emptyMap<String, ExpandableBinaryDictionary>(), null)

        val closeDictMethod = dictGroupClass.getDeclaredMethod("closeDict", String::class.java)
        closeDictMethod.isAccessible = true

        // This should not throw any exceptions
        closeDictMethod.invoke(instance, "nonexistent_dict_type")
    }

    @Test
    fun removeWord_historyOnlyJunkWord_getsBlacklisted() {
        // A junk word (e.g. an auto-learned gesture misfire) that lives only in user history must be
        // blacklisted on removal so it cannot be re-learned and resurrect (the "לא" → "לר" bug).
        val cls = Class.forName("helium314.keyboard.latin.DictionaryGroup")
        val ctor = cls.declaredConstructors.first { it.parameterCount == 4 }.apply { isAccessible = true }
        val history = mock(ExpandableBinaryDictionary::class.java)
        val instance = ctor.newInstance(Locale.ENGLISH, null, mapOf(Dictionary.TYPE_USER_HISTORY to history), null)
        val removeWord = cls.getDeclaredMethod("removeWord", String::class.java).apply { isAccessible = true }
        val isBlacklisted = cls.getDeclaredMethod("isBlacklisted", String::class.java).apply { isAccessible = true }

        assertEquals(false, isBlacklisted.invoke(instance, "לר"))
        removeWord.invoke(instance, "לר")
        assertEquals(true, isBlacklisted.invoke(instance, "לר"))
        verify(history).removeUnigramEntryDynamically("לר")
    }

    @Test
    fun removeWord_mainDictWord_isBlacklisted() {
        // Regression: removing a real main-dictionary word still blacklists it (read-only path).
        val cls = Class.forName("helium314.keyboard.latin.DictionaryGroup")
        val ctor = cls.declaredConstructors.first { it.parameterCount == 4 }.apply { isAccessible = true }
        val main = mock(Dictionary::class.java)
        Mockito.`when`(main.isValidWord("the")).thenReturn(true)
        val instance = ctor.newInstance(Locale.ENGLISH, main, emptyMap<String, ExpandableBinaryDictionary>(), null)
        val removeWord = cls.getDeclaredMethod("removeWord", String::class.java).apply { isAccessible = true }
        val isBlacklisted = cls.getDeclaredMethod("isBlacklisted", String::class.java).apply { isAccessible = true }

        removeWord.invoke(instance, "the")
        assertEquals(true, isBlacklisted.invoke(instance, "the"))
    }

    @Test
    fun isInReadOnlyDict_trueOnlyForReadOnlyDictWords() {
        // Drives whether committing a word un-blacklists it: real words (in a read-only dict) are
        // restored on commit, junk words are not.
        val cls = Class.forName("helium314.keyboard.latin.DictionaryGroup")
        val ctor = cls.declaredConstructors.first { it.parameterCount == 4 }.apply { isAccessible = true }
        val main = mock(Dictionary::class.java)
        Mockito.`when`(main.isValidWord("real")).thenReturn(true)
        val instance = ctor.newInstance(Locale.ENGLISH, main, emptyMap<String, ExpandableBinaryDictionary>(), null)
        val isInReadOnly = cls.getDeclaredMethod("isInReadOnlyDict", String::class.java).apply { isAccessible = true }

        assertEquals(true, isInReadOnly.invoke(instance, "real"))
        assertEquals(false, isInReadOnly.invoke(instance, "לר"))
    }
}
