/*
 * SwipeGestureEngine - gesture path matching for HeliBoard.
 *
 * Algorithm: arc-length resampling + L2 distance scoring.
 * Each word in the dictionary is pre-mapped to a path of N_PTS evenly-spaced
 * (x, y) points (normalized to keyboard dimensions).  On gesture end the input
 * stroke is resampled the same way and candidates are ranked by L2 distance
 * with a small log-frequency bonus.
 */
package helium314.keyboard.latin.gesture;

import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.Keyboard;
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo;
import helium314.keyboard.latin.common.InputPointers;
import helium314.keyboard.latin.dictionary.Dictionary;
import helium314.keyboard.latin.utils.SuggestionResults;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SwipeGestureEngine {

    private static final int N_PTS = 16;
    private static final float FREQ_WEIGHT = 0.05f;

    // ── Self-learning: boost words user actually confirmed via gesture ─────────
    // ponytail: ConcurrentHashMap so corrections from any thread don't corrupt state
    private static final ConcurrentHashMap<String, Integer> sUserBoost = new ConcurrentHashMap<>();
    private static final int USER_BOOST_MAX = 50; // cap to avoid runaway inflation

    /** Call when user selects a gesture suggestion — bumps its score in future matches. */
    public static void recordAccepted(String word) {
        if (word == null || word.isEmpty()) return;
        String key = word.toLowerCase(Locale.ROOT);
        sUserBoost.merge(key, 1, (a, b) -> Math.min(a + b, USER_BOOST_MAX));
    }

    // ── Precomputed index ─────────────────────────────────────────────────────

    public static class IndexEntry {
        public final String word;
        public final float[] path;  // length N_PTS*2
        public final int frequency;
        // ponytail: cache path length to avoid recomputing every ranking call
        public final float pathLen;
        IndexEntry(String word, float[] path, int frequency) {
            this.word = word;
            this.path = path;
            this.frequency = frequency;
            this.pathLen = pathLength(path);
        }
    }

    public static class GestureIndex {
        public final Map<Character, List<IndexEntry>> byFirst;
        // ponytail: store charToPos in index so rankByIndex doesn't rebuild it every call
        public final float[][] charToPos;
        GestureIndex(Map<Character, List<IndexEntry>> byFirst, float[][] charToPos) {
            this.byFirst = byFirst;
            this.charToPos = charToPos;
        }
    }

    public static GestureIndex buildIndex(Map<String, Integer> wordsWithFreq, Keyboard keyboard) {
        float[][] charToPos = buildCharToPos(keyboard);
        Map<Character, List<IndexEntry>> byFirst = new HashMap<>();
        for (Map.Entry<String, Integer> entry : wordsWithFreq.entrySet()) {
            String raw = entry.getKey();
            int freq = entry.getValue() != null ? entry.getValue() : 0;
            // ponytail: apply user boost to freq so self-learned words rank higher immediately
            String lk = raw.toLowerCase(Locale.ROOT);
            Integer boost = sUserBoost.get(lk);
            if (boost != null) freq = Math.min(freq + boost * 5, 255);
            if (freq < 3) continue;
            String word = lk;
            if (word.isEmpty()) continue;
            char first = word.charAt(0);
            if (first < 'a' || first > 'z') continue;
            float[] path = wordPath(word, charToPos);
            byFirst.computeIfAbsent(first, k -> new ArrayList<>())
                    .add(new IndexEntry(raw, path, freq));
        }
        return new GestureIndex(byFirst, charToPos);
    }

    public static int layoutFingerprint(Keyboard keyboard) {
        return Arrays.deepHashCode(buildCharToPos(keyboard));
    }

    // ── Public matching API ───────────────────────────────────────────────────

    private static boolean isAsciiLetter(int code) {
        return (code >= 'a' && code <= 'z') || (code >= 'A' && code <= 'Z');
    }

    // ponytail: use charToPos directly instead of iterating all keys on every gesture
    private static List<Character> nearestLettersFromMap(float nx, float ny, float[][] charToPos) {
        float minDist = Float.MAX_VALUE;
        float[] dists = new float[26];
        for (int i = 0; i < 26; i++) {
            float cx = charToPos[i][0], cy = charToPos[i][1];
            if (cx == 0f && cy == 0f) { dists[i] = Float.MAX_VALUE; continue; }
            float d = (nx - cx) * (nx - cx) + (ny - cy) * (ny - cy);
            dists[i] = d;
            if (d < minDist) minDist = d;
        }
        List<Character> results = new ArrayList<>(4);
        float threshold = minDist + 0.02f;
        for (int i = 0; i < 26; i++) {
            if (dists[i] <= threshold) results.add((char) ('a' + i));
        }
        return results;
    }

    // kept public for external callers (e.g. tests)
    public static List<Character> nearestLetters(int x, int y, Keyboard keyboard) {
        float kw = keyboard.mOccupiedWidth, kh = keyboard.mOccupiedHeight;
        return nearestLettersFromMap(x / kw, y / kh, buildCharToPos(keyboard));
    }

    public static boolean isSequenceMatch(String word, float[] path, float[][] charToPos) {
        int n = path.length / 2;
        int pathIdx = 0;
        char lastChar = 0;
        String w = word.toLowerCase(Locale.ROOT);
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            if (c < 'a' || c > 'z') continue;
            if (c == lastChar) continue;
            float[] target = charToPos[c - 'a'];
            if (target[0] == 0f && target[1] == 0f) continue;
            boolean found = false;
            while (pathIdx < n) {
                float dx = path[2 * pathIdx] - target[0];
                float dy = path[2 * pathIdx + 1] - target[1];
                if (dx * dx + dy * dy <= 0.05f) { found = true; break; }
                pathIdx++;
            }
            if (!found) return false;
            lastChar = c;
        }
        return true;
    }

    public static SuggestionResults rankByIndex(
            GestureIndex index,
            InputPointers pointers,
            Keyboard keyboard,
            int maxResults,
            java.util.Set<String> predictionSet
    ) {
        int n = pointers.getPointerSize();
        SuggestionResults empty = new SuggestionResults(1, false, false);
        if (n < 2 || index == null) return empty;

        int[] xs = pointers.getXCoordinates();
        int[] ys = pointers.getYCoordinates();
        float kw = keyboard.mOccupiedWidth, kh = keyboard.mOccupiedHeight;

        // ponytail: use charToPos from index — already built, no reallocation
        float[][] charToPos = index.charToPos;

        List<Character> startLetters = nearestLettersFromMap(xs[0] / kw, ys[0] / kh, charToPos);
        List<Character> endLetters   = nearestLettersFromMap(xs[n-1] / kw, ys[n-1] / kh, charToPos);

        List<IndexEntry> candidates = new ArrayList<>();
        for (char first : startLetters) {
            List<IndexEntry> list = index.byFirst.get(first);
            if (list != null) candidates.addAll(list);
        }
        if (candidates.isEmpty()) return empty;

        // ponytail: build flat input path inline, no ArrayList<float[]> allocation
        float[] rawFlat = new float[n * 2];
        for (int i = 0; i < n; i++) {
            rawFlat[2 * i]     = xs[i] / kw;
            rawFlat[2 * i + 1] = ys[i] / kh;
        }
        float[] inputVec = resampleFlat(rawFlat, n, N_PTS);
        float inputLength = pathLength(inputVec);

        // Filter by last letter first; relax if empty
        List<IndexEntry> filtered = new ArrayList<>(candidates.size());
        for (IndexEntry e : candidates) {
            String w = e.word.toLowerCase(Locale.ROOT);
            if (!w.isEmpty() && endLetters.contains(w.charAt(w.length() - 1)))
                filtered.add(e);
        }
        if (filtered.isEmpty()) filtered = candidates;

        int m = filtered.size();

        // ponytail: parallel float[] + int[] sort avoids Integer boxing
        float[] scores = new float[m];
        int[]   order  = new int[m];
        for (int i = 0; i < m; i++) {
            IndexEntry e = filtered.get(i);
            float freqBonus = (e.frequency > 0) ? (float)(Math.log(e.frequency + 1) * FREQ_WEIGHT) : 0f;
            boolean seqMatch = isSequenceMatch(e.word, inputVec, charToPos);
            float seqPenalty = seqMatch ? 0f : -0.4f;
            boolean isPredicted = predictionSet != null && predictionSet.contains(e.word.toLowerCase(Locale.ROOT));
            float predBonus = isPredicted ? 0.15f : 0f;
            // ponytail: use precomputed pathLen from IndexEntry instead of recalculating
            float lenPenalty = -Math.abs(inputLength - e.pathLen) * 0.4f;
            // ponytail: apply self-learning user boost at ranking time too
            String lk = e.word.toLowerCase(Locale.ROOT);
            Integer ub = sUserBoost.get(lk);
            float userBonus = ub != null ? (float) Math.log(ub + 1) * 0.08f : 0f;
            scores[i] = -l2(inputVec, e.path) + freqBonus + seqPenalty + predBonus + lenPenalty + userBonus;
            order[i] = i;
        }

        // ponytail: primitive int sort with insertion sort for small N (fast for <500 items)
        for (int i = 1; i < m; i++) {
            int key = order[i];
            float ks = scores[key];
            int j = i - 1;
            while (j >= 0 && scores[order[j]] < ks) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = key;
        }

        int take = Math.min(maxResults, m);
        SuggestionResults result = new SuggestionResults(take, false, false);
        int baseScore = 1_000_000;
        for (int rank = 0; rank < take; rank++) {
            IndexEntry e = filtered.get(order[rank]);
            result.add(new SuggestedWordInfo(
                    e.word, "",
                    baseScore - rank * 1000,
                    SuggestedWordInfo.KIND_CORRECTION,
                    Dictionary.DICTIONARY_USER_TYPED,
                    SuggestedWordInfo.NOT_AN_INDEX,
                    SuggestedWordInfo.NOT_A_CONFIDENCE
            ));
        }
        return result;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private static float pathLength(float[] path) {
        float len = 0;
        int n = path.length / 2;
        for (int i = 0; i < n - 1; i++) {
            float dx = path[2 * (i + 1)] - path[2 * i];
            float dy = path[2 * (i + 1) + 1] - path[2 * i + 1];
            len += (float) Math.sqrt(dx * dx + dy * dy);
        }
        return len;
    }

    static float[][] buildCharToPos(Keyboard keyboard) {
        float[][] map = new float[26][2];
        float kw = keyboard.mOccupiedWidth, kh = keyboard.mOccupiedHeight;
        for (Key key : keyboard.getSortedKeys()) {
            int code = key.getCode();
            if (!isAsciiLetter(code)) continue;
            int idx = Character.toLowerCase((char) code) - 'a';
            map[idx][0] = (key.getX() + key.getWidth()  / 2f) / kw;
            map[idx][1] = (key.getY() + key.getHeight() / 2f) / kh;
        }
        return map;
    }

    static float[] wordPath(String word, float[][] charToPos) {
        float[] pts = new float[word.length() * 2];
        int count = 0;
        float lastX = -1f, lastY = -1f;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            int idx = c - 'a';
            if (idx < 0 || idx >= 26) continue;
            float[] p = charToPos[idx];
            if (count == 0 || p[0] != lastX || p[1] != lastY) {
                pts[2 * count] = p[0];
                pts[2 * count + 1] = p[1];
                lastX = p[0]; lastY = p[1];
                count++;
            }
        }
        return resampleFlat(pts, count, N_PTS);
    }

    static float[] resampleFlat(float[] pts, int numPts, int n) {
        if (numPts == 0) return new float[n * 2];
        if (numPts == 1) {
            float[] r = new float[n * 2];
            float x = pts[0], y = pts[1];
            for (int i = 0; i < n; i++) { r[2*i] = x; r[2*i+1] = y; }
            return r;
        }
        float[] cum = new float[numPts];
        for (int i = 1; i < numPts; i++) {
            float dx = pts[2 * i] - pts[2 * (i - 1)];
            float dy = pts[2 * i + 1] - pts[2 * (i - 1) + 1];
            cum[i] = cum[i-1] + (float) Math.sqrt(dx*dx + dy*dy);
        }
        float total = cum[numPts-1];
        if (total < 1e-9f) {
            float[] r = new float[n * 2];
            float x = pts[0], y = pts[1];
            for (int i = 0; i < n; i++) { r[2*i] = x; r[2*i+1] = y; }
            return r;
        }
        float[] result = new float[n * 2];
        int seg = 0;
        for (int i = 0; i < n; i++) {
            float t = total * i / (n - 1);
            while (seg < numPts - 2 && cum[seg + 1] < t) seg++;
            float segLen = cum[seg+1] - cum[seg];
            float alpha  = (segLen > 1e-9f) ? (t - cum[seg]) / segLen : 0f;
            result[2*i]   = pts[2 * seg] + alpha * (pts[2 * (seg + 1)] - pts[2 * seg]);
            result[2*i+1] = pts[2 * seg + 1] + alpha * (pts[2 * (seg + 1) + 1] - pts[2 * seg + 1]);
        }
        return result;
    }

    // ponytail: kept for compat, delegates to resampleFlat
    static float[] resample(List<float[]> pts, int n) {
        float[] flat = new float[pts.size() * 2];
        for (int i = 0; i < pts.size(); i++) {
            flat[2*i] = pts.get(i)[0];
            flat[2*i+1] = pts.get(i)[1];
        }
        return resampleFlat(flat, pts.size(), n);
    }

    private static float l2(float[] a, float[] b) {
        float s = 0;
        int n = a.length / 2;
        for (int i = 0; i < n; i++) {
            float dx = a[2 * i] - b[2 * i];
            float dy = a[2 * i + 1] - b[2 * i + 1];
            float distSq = dx * dx + dy * dy;
            // ponytail: weight endpoints twice — more precisely typed
            if (i == 0 || i == n - 1) s += distSq * 2.0f;
            else s += distSq;
        }
        return (float) Math.sqrt(s);
    }
}
