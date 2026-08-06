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

import android.content.Context;
import android.graphics.Rect;
import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.Keyboard;
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo;
import helium314.keyboard.latin.common.InputPointers;
import helium314.keyboard.latin.dictionary.Dictionary;
import helium314.keyboard.latin.utils.SuggestionResults;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SwipeGestureEngine {

    private static final ExecutorService sSaveExecutor = Executors.newSingleThreadExecutor();

    private static final int N_PTS = 16;
    private static final float FREQ_WEIGHT = 0.05f;

    // ── Self-learning: boost words user actually confirmed via gesture ─────────
    // ponytail: ConcurrentHashMap so corrections from any thread don't corrupt state
    private static final ConcurrentHashMap<String, Integer> sUserBoost = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, float[]> sUserPaths = new ConcurrentHashMap<>();
    private static final int USER_BOOST_MAX = 50; // cap to avoid runaway inflation
    private static final float[] sUserBoostCache = new float[USER_BOOST_MAX + 1];
    static {
        for (int i = 0; i <= USER_BOOST_MAX; i++) {
            sUserBoostCache[i] = (float) Math.log(i + 1) * 0.08f;
        }
    }

    private static File sUserDataFile = null;

    public static void initialize(Context context) {
        if (sUserDataFile != null) return;
        sUserDataFile = new File(context.getFilesDir(), "gesture_user_data.bin");
        loadUserData();
    }

    /** Call when user selects a gesture suggestion — bumps its score and saves their swipe path. */
    public static void recordAccepted(String word, InputPointers pointers, Keyboard keyboard, GestureIndex activeIndex) {
        if (word == null || word.isEmpty()) return;
        String key = word.toLowerCase(Locale.ROOT);
        sUserBoost.merge(key, 1, (a, b) -> Math.min(a + b, USER_BOOST_MAX));

        if (pointers != null && pointers.getPointerSize() >= 2 && keyboard != null) {
            int n = pointers.getPointerSize();
            int[] xs = pointers.getXCoordinates();
            int[] ys = pointers.getYCoordinates();
            float kw = keyboard.mOccupiedWidth, kh = keyboard.mOccupiedHeight;
            float[] rawFlat = new float[n * 2];
            for (int i = 0; i < n; i++) {
                rawFlat[2 * i]     = xs[i] / kw;
                rawFlat[2 * i + 1] = ys[i] / kh;
            }
            float[] inputVec = resampleFlat(rawFlat, n, N_PTS);
            sUserPaths.put(key, inputVec);

            // Update active index in-place if provided
            if (activeIndex != null && !key.isEmpty()) {
                char first = key.charAt(0);
                List<IndexEntry> list = activeIndex.byFirst.get(first);
                if (list != null) {
                    for (IndexEntry entry : list) {
                        if (getLowerCase(entry.word).equals(key)) {
                            float[] path = new float[N_PTS * 2];
                            entry.unpackPath(path);
                            for (int i = 0; i < N_PTS * 2; i++) {
                                path[i] = path[i] * 0.3f + inputVec[i] * 0.7f;
                            }
                            entry.updatePath(path);
                            break;
                        }
                    }
                }
            }
        }

        saveUserDataAsync();
    }

    private static void saveUserData() {
        if (sUserDataFile == null) return;
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(
                new java.io.BufferedOutputStream(new java.io.FileOutputStream(sUserDataFile)))) {
            out.writeInt(1); // format version
            
            // Save boosts
            out.writeInt(sUserBoost.size());
            for (Map.Entry<String, Integer> entry : sUserBoost.entrySet()) {
                out.writeUTF(entry.getKey());
                out.writeInt(entry.getValue());
            }
            
            // Save paths
            out.writeInt(sUserPaths.size());
            for (Map.Entry<String, float[]> entry : sUserPaths.entrySet()) {
                out.writeUTF(entry.getKey());
                float[] path = entry.getValue();
                for (int i = 0; i < N_PTS * 2; i++) {
                    out.writeFloat(path[i]);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("SwipeGestureEngine", "Error saving user data", e);
        }
    }

    private static void saveUserDataAsync() {
        sSaveExecutor.execute(() -> {
            synchronized (SwipeGestureEngine.class) {
                saveUserData();
            }
        });
    }

    private static void loadUserData() {
        if (sUserDataFile == null || !sUserDataFile.exists()) return;
        synchronized (SwipeGestureEngine.class) {
            try (java.io.DataInputStream in = new java.io.DataInputStream(
                    new java.io.BufferedInputStream(new java.io.FileInputStream(sUserDataFile)))) {
                int version = in.readInt();
                if (version != 1) return;
                
                sUserBoost.clear();
                int numBoosts = in.readInt();
                for (int i = 0; i < numBoosts; i++) {
                    String key = in.readUTF();
                    int count = in.readInt();
                    sUserBoost.put(key, count);
                }
                
                sUserPaths.clear();
                int numPaths = in.readInt();
                for (int i = 0; i < numPaths; i++) {
                    String key = in.readUTF();
                    float[] path = new float[N_PTS * 2];
                    for (int j = 0; j < N_PTS * 2; j++) {
                        path[j] = in.readFloat();
                    }
                    sUserPaths.put(key, path);
                }
            } catch (Exception e) {
                android.util.Log.e("SwipeGestureEngine", "Error loading user data", e);
            }
        }
    }

    // ── Precomputed index ─────────────────────────────────────────────────────

    private static String getLowerCase(String s) {
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                return s.toLowerCase(Locale.ROOT);
            }
        }
        return s;
    }

    private static long pack8Bytes(float[] pts, int startIndex) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            float f = pts[startIndex + i];
            if (f < 0f) f = 0f;
            else if (f > 1f) f = 1f;
            int b = Math.round(f * 255f) & 0xFF;
            value |= ((long) b) << (i * 8);
        }
        return value;
    }

    private static void unpack8Bytes(long value, float[] out, int startIndex) {
        for (int i = 0; i < 8; i++) {
            int b = (int) ((value >>> (i * 8)) & 0xFF);
            out[startIndex + i] = b / 255f;
        }
    }

    public static class IndexEntry {
        public final String word;
        public final int frequency;
        // ponytail: cache path length and freq bonus to avoid recomputing every ranking call
        public float pathLen;
        public final float freqBonus;
        private long path0;
        private long path1;
        private long path2;
        private long path3;

        IndexEntry(String word, float[] path, int frequency) {
            this.word = word;
            this.frequency = frequency;
            this.freqBonus = (frequency > 0) ? (float)(Math.log(frequency + 1) * FREQ_WEIGHT) : 0f;
            
            String lk = getLowerCase(word);
            float[] blended = path;
            float[] userPath = sUserPaths.get(lk);
            if (userPath != null && userPath.length == N_PTS * 2) {
                blended = new float[N_PTS * 2];
                for (int i = 0; i < N_PTS * 2; i++) {
                    blended[i] = path[i] * 0.3f + userPath[i] * 0.7f;
                }
            }
            updatePath(blended);
        }

        public void updatePath(float[] newPath) {
            this.path0 = pack8Bytes(newPath, 0);
            this.path1 = pack8Bytes(newPath, 8);
            this.path2 = pack8Bytes(newPath, 16);
            this.path3 = pack8Bytes(newPath, 24);
            this.pathLen = pathLength(newPath);
        }

        public void unpackPath(float[] out) {
            unpack8Bytes(path0, out, 0);
            unpack8Bytes(path1, out, 8);
            unpack8Bytes(path2, out, 16);
            unpack8Bytes(path3, out, 24);
        }
    }

    public static class GestureIndex {
        public final Map<Character, List<IndexEntry>> byFirst;
        // ponytail: store charToPos in index so rankByIndex doesn't rebuild it every call
        public final Map<Character, float[]> charToPos;
        GestureIndex(Map<Character, List<IndexEntry>> byFirst, Map<Character, float[]> charToPos) {
            this.byFirst = byFirst;
            this.charToPos = charToPos;
        }
    }

    public static volatile boolean isCancelled = false;

    public static void cancelIndexing() {
        isCancelled = true;
    }

    public static GestureIndex buildIndex(helium314.keyboard.latin.DictionaryFacilitator facilitator, Keyboard keyboard) {
        isCancelled = false;
        Map<Character, float[]> charToPos = buildCharToPos(keyboard);
        Map<Character, List<IndexEntry>> byFirst = new HashMap<>();
        try {
            facilitator.forEachMainDictionaryWord((raw, freqVal) -> {
                if (isCancelled) return;
                if (raw == null) return;
                if (facilitator.isBlacklisted(raw)) return;
                int freq = freqVal != null ? freqVal : 0;
                // ponytail: apply user boost to freq so self-learned words rank higher immediately
                String lk = getLowerCase(raw);
                Integer boost = sUserBoost.get(lk);
                if (boost != null) freq = Math.min(freq + boost * 5, 255);
                if (freq < 12) return;
                String word = lk;
                if (word.isEmpty()) return;
                char first = word.charAt(0);
                if (!charToPos.containsKey(first)) return;
                float[] path = wordPath(word, charToPos);
                byFirst.computeIfAbsent(first, k -> new ArrayList<>())
                        .add(new IndexEntry(word, path, freq));
            });
            for (Map.Entry<Character, List<IndexEntry>> entry : byFirst.entrySet()) {
                List<IndexEntry> list = entry.getValue();
                list.sort((a, b) -> Integer.compare(b.frequency, a.frequency));
                if (list.size() > 2000) {
                    entry.setValue(new ArrayList<>(list.subList(0, 2000)));
                }
            }
        } catch (OutOfMemoryError e) {
            android.util.Log.e("SwipeGestureEngine", "OOM building gesture index, using partial index", e);
            System.gc();
        }
        return new GestureIndex(byFirst, charToPos);
    }

    public static int layoutFingerprint(Keyboard keyboard) {
        Map<Character, float[]> map = buildCharToPos(keyboard);
        Object[] values = new Object[map.size()];
        int idx = 0;
        for (float[] p : map.values()) {
            values[idx++] = p;
        }
        return Arrays.deepHashCode(values);
    }

    // ── Public matching API ───────────────────────────────────────────────────

    private static boolean isAsciiLetter(int code) {
        return (code >= 'a' && code <= 'z') || (code >= 'A' && code <= 'Z');
    }

    // ponytail: use charToPos directly instead of iterating all keys on every gesture
    private static List<Character> nearestLettersFromMap(float nx, float ny, Map<Character, float[]> charToPos) {
        float minDist = Float.MAX_VALUE;
        Map<Character, Float> dists = new HashMap<>();
        for (Map.Entry<Character, float[]> entry : charToPos.entrySet()) {
            float[] pos = entry.getValue();
            float cx = pos[0], cy = pos[1];
            float d = (nx - cx) * (nx - cx) + (ny - cy) * (ny - cy);
            dists.put(entry.getKey(), d);
            if (d < minDist) minDist = d;
        }
        List<Character> results = new ArrayList<>(4);
        float threshold = minDist + 0.035f;
        for (Map.Entry<Character, Float> entry : dists.entrySet()) {
            if (entry.getValue() <= threshold) results.add(entry.getKey());
        }
        return results;
    }

    // kept public for external callers (e.g. tests)
    public static List<Character> nearestLetters(int x, int y, Keyboard keyboard) {
        float kw = keyboard.mOccupiedWidth, kh = keyboard.mOccupiedHeight;
        return nearestLettersFromMap(x / kw, y / kh, buildCharToPos(keyboard));
    }

    private static float sqDistanceToSegment(float px, float py, float ax, float ay, float bx, float by, float[] outT) {
        float dx = bx - ax;
        float dy = by - ay;
        float segmentLenSq = dx * dx + dy * dy;
        if (segmentLenSq < 1e-9f) {
            outT[0] = 0f;
            return (px - ax) * (px - ax) + (py - ay) * (py - ay);
        }
        float t = ((px - ax) * dx + (py - ay) * dy) / segmentLenSq;
        if (t < 0f) t = 0f;
        else if (t > 1f) t = 1f;
        outT[0] = t;
        float closestX = ax + t * dx;
        float closestY = ay + t * dy;
        return (px - closestX) * (px - closestX) + (py - closestY) * (py - closestY);
    }

    public static boolean isSequenceMatch(String word, float[] path, Map<Character, float[]> charToPos) {
        int n = path.length / 2;
        int segmentIdx = 0;
        float prevT = -0.01f;
        char lastChar = 0;
        float[] outT = new float[1];
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c == lastChar) continue;
            float[] target = charToPos.get(c);
            if (target == null) continue;
            boolean found = false;
            while (segmentIdx < n - 1) {
                float distSq = sqDistanceToSegment(target[0], target[1],
                        path[2 * segmentIdx], path[2 * segmentIdx + 1],
                        path[2 * (segmentIdx + 1)], path[2 * (segmentIdx + 1) + 1], outT);
                if (distSq <= 0.05f) {
                    float t = outT[0];
                    if (t > prevT) {
                        prevT = t;
                        found = true;
                        break;
                    }
                }
                segmentIdx++;
                prevT = -0.01f;
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
        Map<Character, float[]> charToPos = index.charToPos;

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
            String lower = getLowerCase(e.word);
            if (!lower.isEmpty() && endLetters.contains(lower.charAt(lower.length() - 1)))
                filtered.add(e);
        }
        if (filtered.isEmpty()) filtered = candidates;

        int m = filtered.size();

        // ponytail: parallel float[] + int[] sort avoids Integer boxing
        float[] scores = new float[m];
        int[]   order  = new int[m];
        int count = 0;
        float[] topScores = new float[maxResults];
        Arrays.fill(topScores, -Float.MAX_VALUE);
        float threshold = -Float.MAX_VALUE;

        float[] candidatePath = new float[N_PTS * 2];
        for (int i = 0; i < m; i++) {
            IndexEntry e = filtered.get(i);
            String lower = getLowerCase(e.word);
            boolean isPredicted = predictionSet != null && predictionSet.contains(lower);
            float predBonus = isPredicted ? 0.15f : 0f;
            float lenPenalty = -Math.abs(inputLength - e.pathLen) * 0.4f;
            Integer ub = sUserBoost.get(lower);
            float userBonus = ub != null ? sUserBoostCache[ub] : 0f;

            float bonuses = e.freqBonus + predBonus + lenPenalty + userBonus;
            if (bonuses < threshold) {
                continue;
            }

            boolean seqMatch = isSequenceMatch(lower, inputVec, charToPos);
            float seqPenalty = seqMatch ? 0f : -0.4f;
            float scoreWithSeq = bonuses + seqPenalty;
            if (scoreWithSeq < threshold) {
                continue;
            }

            e.unpackPath(candidatePath);
            float maxL2 = (threshold == -Float.MAX_VALUE) ? Float.MAX_VALUE : (scoreWithSeq - threshold);
            float distance = l2(inputVec, candidatePath, maxL2);
            float score = -distance + scoreWithSeq;

            scores[count] = score;
            order[count] = i;
            count++;

            if (score > threshold) {
                threshold = updateThreshold(topScores, score);
            }
        }

        if (count == 0) return empty;

        // ponytail: primitive int sort with insertion sort for small N (fast for <500 items)
        for (int i = 1; i < count; i++) {
            int key = order[i];
            float ks = scores[i];
            int j = i - 1;
            while (j >= 0 && scores[j] < ks) {
                scores[j + 1] = scores[j];
                order[j + 1] = order[j];
                j--;
            }
            scores[j + 1] = ks;
            order[j + 1] = key;
        }

        int take = Math.min(maxResults, count);
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

    static Map<Character, float[]> buildCharToPos(Keyboard keyboard) {
        Map<Character, float[]> map = new HashMap<>();
        float kw = keyboard.mOccupiedWidth, kh = keyboard.mOccupiedHeight;
        for (Key key : keyboard.getSortedKeys()) {
            int code = key.getCode();
            if (code <= 0) continue;
            char c = Character.toLowerCase((char) code);
            Rect hitBox = key.getHitBox();
            map.put(c, new float[]{hitBox.exactCenterX() / kw, hitBox.exactCenterY() / kh});
        }
        return map;
    }

    static float[] wordPath(String word, Map<Character, float[]> charToPos) {
        float[] pts = new float[word.length() * 2];
        int count = 0;
        float lastX = -1f, lastY = -1f;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            float[] p = charToPos.get(c);
            if (p == null) continue;
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

    private static float l2(float[] a, float[] b, float maxL2) {
        float s = 0;
        int n = a.length / 2;
        float limitSq = maxL2 * maxL2;
        for (int i = 0; i < n; i++) {
            float dx = a[2 * i] - b[2 * i];
            float dy = a[2 * i + 1] - b[2 * i + 1];
            float distSq = dx * dx + dy * dy;
            // ponytail: weight endpoints twice — more precisely typed
            if (i == 0 || i == n - 1) s += distSq * 2.0f;
            else s += distSq;
            if (s > limitSq) return Float.MAX_VALUE;
        }
        return (float) Math.sqrt(s);
    }

    private static float updateThreshold(float[] topScores, float newScore) {
        int minIdx = 0;
        for (int i = 1; i < topScores.length; i++) {
            if (topScores[i] < topScores[minIdx]) minIdx = i;
        }
        if (newScore > topScores[minIdx]) {
            topScores[minIdx] = newScore;
        }
        float min = topScores[0];
        for (int i = 1; i < topScores.length; i++) {
            if (topScores[i] < min) min = topScores[i];
        }
        return min;
    }

    public static boolean hasLoopAtEnd(InputPointers pointers, Keyboard keyboard) {
        int n = pointers.getPointerSize();
        if (n < 6) return false;
        int[] xs = pointers.getXCoordinates();
        int[] ys = pointers.getYCoordinates();
        
        // Look at the last min(n/2, 10) points
        int pointsToCheck = Math.min(n / 2, 10);
        if (pointsToCheck < 4) pointsToCheck = 4;
        int startIdx = n - pointsToCheck;
        
        float pathLen = 0f;
        for (int i = startIdx; i < n - 1; i++) {
            float dx = xs[i+1] - xs[i];
            float dy = ys[i+1] - ys[i];
            pathLen += (float) Math.sqrt(dx * dx + dy * dy);
        }
        
        float startEndX = xs[n - 1] - xs[startIdx];
        float startEndY = ys[n - 1] - ys[startIdx];
        float displacement = (float) Math.sqrt(startEndX * startEndX + startEndY * startEndY);
        
        float kw = keyboard.mOccupiedWidth;
        // Make sure the loop is physically large enough to be a deliberate loop, not finger jitter
        if (pathLen < kw * 0.02f) return false;
        
        return pathLen > 2.0f * displacement;
    }
}
