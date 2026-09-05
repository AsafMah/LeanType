package helium314.keyboard.latin.dictionary;

import android.content.Context;
import com.android.inputmethod.latin.BinaryDictionary;

import helium314.keyboard.latin.NgramContext;
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo;
import helium314.keyboard.latin.common.ComposedData;
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.mockito.Mockito;

/** Replaces only native storage; provider insertion, loading and completion remain production code. */
public final class InMemoryUserDictionary extends UserBinaryDictionary {
    private final Set<String> mWords = new LinkedHashSet<>();
    public boolean mReturnPredictions;

    public InMemoryUserDictionary(final Context context) {
        super(context, Locale.ENGLISH, false,
                new File(context.getFilesDir(), "mutation-lifecycle.dict"), "mutation-lifecycle");
        final BinaryDictionary nativeDictionary = Mockito.mock(BinaryDictionary.class);
        Mockito.when(nativeDictionary.isValidDictionary()).thenReturn(true);
        Mockito.when(nativeDictionary.removeUnigramEntry(Mockito.anyString()))
                .thenAnswer(invocation -> mWords.remove(invocation.getArgument(0)));
        try {
            final java.lang.reflect.Field field = ExpandableBinaryDictionary.class
                    .getDeclaredField("mBinaryDictionary");
            field.setAccessible(true);
            field.set(this, nativeDictionary);
        } catch (final ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public void markDirty() {
        setNeedsToRecreate();
    }

    @Override
    void createNewDictionaryLocked() {
        mWords.clear();
        loadInitialContentsLocked();
        try {
            new File(mContext.getFilesDir(), "mutation-lifecycle.dict").createNewFile();
        } catch (final IOException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    protected void runGCIfRequiredLocked(final boolean mindsBlockByGC) {
    }

    @Override
    protected void addUnigramLocked(final String word, final int frequency,
            final String shortcutTarget, final int shortcutFreq, final boolean isNotAWord,
            final boolean isPossiblyOffensive, final int timestamp) {
        mWords.add(word);
    }

    @Override
    protected boolean isInDictionaryLocked(final String word) {
        return mWords.contains(word);
    }

    @Override
    public boolean isInDictionary(final String word) {
        return mWords.contains(word);
    }

    @Override
    public boolean isValidWord(final String word) {
        return mWords.contains(word);
    }

    @Override
    public ArrayList<SuggestedWordInfo> getSuggestions(final ComposedData composedData,
            final NgramContext ngramContext, final long proximityInfoHandle,
            final SettingsValuesForSuggestion settings, final int sessionId,
            final float languageVsSpatialWeight, final float[] languageWeight) {
        final ArrayList<SuggestedWordInfo> suggestions = new ArrayList<>();
        if (mReturnPredictions) {
            for (final String word : mWords) {
                suggestions.add(new SuggestedWordInfo(word, "", 160,
                        SuggestedWordInfo.KIND_PREDICTION, this,
                        SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE));
            }
        }
        return suggestions;
    }

    @Override
    public void forEachWord(final BiConsumer<String, Integer> consumer) {
        mWords.forEach(word -> consumer.accept(word, 160));
    }

    @Override
    public Map<String, Integer> getAllWordsWithFrequency() {
        final Map<String, Integer> words = new LinkedHashMap<>();
        mWords.forEach(word -> words.put(word, 160));
        return words;
    }
}
