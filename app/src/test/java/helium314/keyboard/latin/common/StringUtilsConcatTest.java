// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Unit tests for {@link StringUtils#concatWithoutDuplicatedPrefix} (B7a, #98). */
public class StringUtilsConcatTest {

    @Test public void appendsContinuationWhenResultIsOnlyTheContinuation() {
        // recognizer returned just the swiped continuation -> plain concat
        assertEquals("technology", StringUtils.concatWithoutDuplicatedPrefix("tech", "nology"));
        assertEquals("silver", StringUtils.concatWithoutDuplicatedPrefix("s", "ilver"));
    }

    @Test public void doesNotDoubleCountWhenRecognizerReturnsTheWholeWord() {
        // the fix: a merged fake-track makes the lib return the whole word -> no "techtechnology"
        assertEquals("technology", StringUtils.concatWithoutDuplicatedPrefix("tech", "technology"));
        assertEquals("silo", StringUtils.concatWithoutDuplicatedPrefix("s", "silo"));
    }

    @Test public void preservesPrefixCasingOnWholeWordReturn() {
        assertEquals("Technology", StringUtils.concatWithoutDuplicatedPrefix("Tech", "technology"));
        assertEquals("TEchnology", StringUtils.concatWithoutDuplicatedPrefix("TEch", "technology"));
    }

    @Test public void concatenatesWhenThereIsNoSharedPrefix() {
        assertEquals("techtea", StringUtils.concatWithoutDuplicatedPrefix("tech", "tea"));
    }

    @Test public void concatenatesWhenContinuationShorterThanPrefix() {
        assertEquals("teche", StringUtils.concatWithoutDuplicatedPrefix("tech", "e"));
    }

    @Test public void emptyPrefixReturnsTextUnchanged() {
        assertEquals("hello", StringUtils.concatWithoutDuplicatedPrefix("", "hello"));
    }
}
