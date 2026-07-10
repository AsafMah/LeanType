/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.dictionary;

import com.android.inputmethod.latin.BinaryDictionary;

import helium314.keyboard.latin.NgramContext;
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo;
import helium314.keyboard.latin.common.ComposedData;
import helium314.keyboard.latin.makedict.WordProperty;
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This class provides binary dictionary reading operations with locking. An instance of this class
 * can be used by multiple threads. Note that different session IDs must be used when multiple
 * threads get suggestions using this class.
 */
public final class ReadOnlyBinaryDictionary extends Dictionary {
    /**
     * A lock for accessing binary dictionary. Only closing binary dictionary is the operation
     * that change the state of dictionary.
     */
    private final ReentrantReadWriteLock mLock = new ReentrantReadWriteLock();

    private final BinaryDictionary mBinaryDictionary;

    public ReadOnlyBinaryDictionary(final String filename, final long offset, final long length,
            final boolean useFullEditDistance, final Locale locale, final String dictType) {
        super(dictType, locale);
        mBinaryDictionary = new BinaryDictionary(filename, offset, length, useFullEditDistance,
                locale, dictType, false /* isUpdatable */);
    }

    public boolean isValidDictionary() {
        return mBinaryDictionary.isValidDictionary();
    }

    @Override
    public ArrayList<SuggestedWordInfo> getSuggestions(final ComposedData composedData,
            final NgramContext ngramContext, final long proximityInfoHandle,
            final SettingsValuesForSuggestion settingsValuesForSuggestion,
            final int sessionId, final float weightForLocale,
            final float[] inOutWeightOfLangModelVsSpatialModel) {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.getSuggestions(composedData, ngramContext,
                        proximityInfoHandle, settingsValuesForSuggestion, sessionId,
                        weightForLocale, inOutWeightOfLangModelVsSpatialModel);
            } finally {
                mLock.readLock().unlock();
            }
        }
        return null;
    }

    @Override
    public boolean isInDictionary(final String word) {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.isInDictionary(word);
            } finally {
                mLock.readLock().unlock();
            }
        }
        return false;
    }

    @Override
    public boolean shouldAutoCommit(final SuggestedWordInfo candidate) {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.shouldAutoCommit(candidate);
            } finally {
                mLock.readLock().unlock();
            }
        }
        return false;
    }

    @Override
    public int getFrequency(final String word) {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.getFrequency(word);
            } finally {
                mLock.readLock().unlock();
            }
        }
        return NOT_A_PROBABILITY;
    }

    @Override
    public int getMaxFrequencyOfExactMatches(final String word) {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.getMaxFrequencyOfExactMatches(word);
            } finally {
                mLock.readLock().unlock();
            }
        }
        return NOT_A_PROBABILITY;
    }

    @Override
    @androidx.annotation.NonNull
    public Map<String, Integer> getAllWordsWithFrequency() {
        Map<String, Integer> words = new HashMap<>();
        int token = 0;
        int count = 0;
        do {
            if (!mLock.readLock().tryLock()) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }
            try {
                if (!mBinaryDictionary.isValidDictionary()) {
                    break;
                }
                BinaryDictionary.GetNextWordAndFrequencyResult result =
                        mBinaryDictionary.getNextWordAndFrequency(token);
                if (result.mWordAndFrequency == null) break;
                String word = result.mWordAndFrequency.mWord;
                int freq = result.mWordAndFrequency.mFrequency;
                if (word != null && !word.isEmpty() && freq >= 0) {
                    words.put(word, freq);
                }
                token = result.mNextToken;
            } finally {
                mLock.readLock().unlock();
            }

            count++;
            if (count % 200 == 0) {
                Thread.yield();
            }
            if (count % 2000 == 0) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } while (token != 0);
        return words;
    }

    @Override
    public void forEachWord(java.util.function.BiConsumer<String, Integer> consumer) {
        int token = 0;
        int count = 0;
        do {
            if (!mLock.readLock().tryLock()) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }
            try {
                if (!mBinaryDictionary.isValidDictionary()) {
                    break;
                }
                BinaryDictionary.GetNextWordAndFrequencyResult result =
                        mBinaryDictionary.getNextWordAndFrequency(token);
                if (result.mWordAndFrequency == null) break;
                String word = result.mWordAndFrequency.mWord;
                int freq = result.mWordAndFrequency.mFrequency;
                if (word != null && !word.isEmpty() && freq >= 0) {
                    consumer.accept(word, freq);
                }
                token = result.mNextToken;
            } finally {
                mLock.readLock().unlock();
            }

            count++;
            if (count % 200 == 0) {
                Thread.yield();
            }
            if (count % 2000 == 0) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } while (token != 0);
    }

    @Override
    public WordProperty getWordProperty(String word, boolean isBeginningOfSentence) {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.getWordProperty(word, isBeginningOfSentence);
            } finally {
                mLock.readLock().unlock();
            }
        }
        return null;
    }

    @Override
    public void close() {
        mLock.writeLock().lock();
        try {
            mBinaryDictionary.close();
        } finally {
            mLock.writeLock().unlock();
        }
    }
}
