# Adaptive Typing — learned per-user key geometry (taps + gestures)

> Status: **design / in progress** (opt-in feature). Tracking issue: see the
> "Adaptive learned key geometry" issue on the fork. This note is the source of
> truth for the design; update it as the implementation evolves.

## Goal

Make the keyboard *feel like it learns how you type* — for both **tapping** and
**gesture/glide** input — by adapting each key's effective touch geometry to where
your finger actually lands and how the current context makes some keys more likely.
This is the technique popular keyboards use ("internally resize keys"); the academic
framing is a spatial model with a context prior (Bayesian touch).

Explicitly **not** this feature: making autocorrect pick a better *word* after the
fact. We want it to feel like "the keyboard adapted to me," not "I still miss keys
but autocorrect cleans up." (That word-ranking idea is captured separately in
`SUGGESTION_RANKING.md`.)

## How the engine works today (why this is feasible)

Two independent geometry systems decide what your input becomes:

1. **Literal tap → key** — `KeyDetector.detectHitKey` (`keyboard/KeyDetector.java`)
   picks the key with the smallest edge-distance (`Key.squaredDistanceToEdge`). Pure
   geometry, no weighting. The exact tapped letter is committed immediately;
   mistakes are fixed downstream by autocorrect.
2. **Spatial model (gestures + tap-correction)** — the native recognizer scores
   candidates using per-key **sweet spots** (effective center + radius). Those sweet
   spots are **computed in Java** in `ProximityInfo.createNativeProximityInfo`
   (`com/android/inputmethod/keyboard/ProximityInfo.java:168-220`) from key hit-box
   centers + the static per-row `TouchPositionCorrection`, then pushed across the
   existing JNI (`setProximityInfoNative`).

**Key consequence:** because the sweet spots are produced in Java and passed through
the existing JNI, we can change what a swipe resolves to **without a native (C++)
rebuild** — we just feed adjusted centers/radii. This is what lets the feature reach
gesture compose, which is a hard requirement.

There is no dynamic or learned key resizing today. A *static* per-row
`TouchPositionCorrection` exists (sweet-spot Y offset + radius per row); its
X-correction is disabled/obsolete.

## The model: one learned model, two consumers

A single per-user model, keyed by **(key, layout, orientation)**, stores content-free
geometry:

```
TOUCH_MODEL(
  key_code, layout, orientation,
  mean_dx, mean_dy,      -- where you land relative to the key center (EMA)
  var_dx,  var_dy,       -- how consistent you are (for confidence + radius scaling)
  count,                 -- samples seen (gates confidence; powers the stats page)
  updated_at
)
```

No characters, words, or sequences are ever stored — only aggregate geometry per key.

Two parts read it:

- **Taps** → `KeyDetector.detectHitKey`: measure distance to each key's **learned**
  center (center + mean offset) instead of the raw center → borderline taps resolve
  the way *you* type.
- **Gestures** → `ProximityInfo` sweet spots: shift each key's center by its learned
  offset and scale its radius by your consistency → the recognizer matches your swipe
  against keys positioned where your hand actually goes.

Same model behind both ⇒ coherent "this is how I type."

### Layer A — dynamic context prior (tap-only, ephemeral, stores nothing)

Each keystroke, read the in-progress word's top-N completion candidates (the engine
already produces ~18 internally; the visible 3 is only a display limit) and project
them to a *next-character* distribution: for each candidate, take the char at the
current position weighted by the candidate's score; sum per letter. Example: typed
`H`, candidates `Hello`/`Hey`/`He` → `e` dominates → **E's tap target grows slightly
for the next tap.** Recomputed live; never persisted.

For **gestures** there is no single "next key" to enlarge mid-stroke, so the context
prior is tap-only. The contextual-likelihood part for gestures is already handled by
the native language model (word-level). The new gesture win is Layer B (learned
geometry).

### Layer B — learned per-user touch model (persisted, the "learning")

- **Record (taps):** on a letter tap, compute `dx = touchX - keyCenterX`,
  `dy = touchY - keyCenterY` and fold into that key's running mean/variance via an
  exponential moving average (recent behavior weighted more; old data decays).
  Implemented in `PointerTracker.recordAdaptiveTouchSample`.
- **Record (gestures):** a swipe also teaches the model, but only via its clean
  **endpoints**: finger-down ≈ the word's first letter, finger-up ≈ its last letter.
  Interior keys are skipped (corner-cutting makes them unreliable) and only fresh single
  strokes count (merged/extended trails have ambiguous ends). Implemented in
  `InputLogic.maybeRecordGestureEndpoints`.
- **Apply:** effective center = `center + mean_offset`; effective radius scales with
  consistency (tighter for keys you nail, more forgiving for scattered keys).

## Safety: never "I pressed W but it typed E"

The literal tap is committed as-is, so the bias is **hard-capped** and confidence-gated:

- Max center shift ≤ ~25% of key width/height (tunable via a strength slider).
- Radius scale bounded (e.g. 0.7–1.4×).
- A key's bias only ramps in after enough samples (`count`) and low-enough variance;
  the applied magnitude scales with confidence — no sudden jumps.
- The context prior only breaks *ambiguous* taps within the cap band. **Beyond the cap
  into a neighbor's territory, the neighbor always wins.** Strength = 0 ⇒ pure learned
  geometry, no context flipping.

## Privacy & security

- Layer A persists nothing.
- Layer B persists only per-key geometry + counts — **no text content.** The only weak
  signal is per-key `count` (letter-usage frequency), which never leaves the device.
- **Respect existing learning gates:** do not record while `mIncognitoModeEnabled`
  (always-incognito pref, framework no-learning fields, or password fields) — the same
  gate user-history learning uses. Opt-in master toggle on top.
- The settings backup already contains clipboard text + user dictionaries, so adding
  content-free geometry does not widen the backup's sensitivity. We may still
  quantize/round counts for extra caution.

## Persistence, export/import, backup compatibility

Local data lives in a single SQLite DB, `leantype.db`
(`latin/database/Database.kt`, raw `SQLiteOpenHelper`, currently VERSION 2; clipboard
lives there). The settings backup (`settings/preferences/BackupRestorePreference.kt`,
Advanced tab) **already zips the entire `leantype.db` and restores it**, and restore
is lenient (unknown zip entries skipped; missing columns handled by schema checks in
`Database.copyFromDb` + `onUpgrade`).

Therefore:

- **Single export/import path (satisfied for free):** add the touch model as a **new
  table in `leantype.db`** → it is automatically part of the existing Advanced-tab
  backup and restored the same way. No second mechanism.
- **Move behaviors to another device:** backup → restore.
- **Delete:** a "Reset learned typing model" button clears the table.
- **Don't break existing setting files:** purely additive — bump `Database.VERSION`,
  add an `onUpgrade` that `CREATE`s the table; old code reads only tables it knows, so
  an old backup (no table → created empty) and a new backup on an older app (unknown
  table ignored) both restore safely. The format is unversioned but tolerant by design.

## Stats / "your learned keyboard" page (Settings)

A visualization page (trust + the reset control live here):

- **Heatmap**: each key with its learned offset (arrow) and a variance/confidence
  ellipse — literally "what your learned keyboard looks like."
- **Per-key accuracy stats**:
  - *Consistency* = how tightly you cluster on the key (low variance).
  - *Correction rate* = how often a tap on the key was immediately backspaced /
    autocorrected away — an **approximate** accuracy proxy (we never know true intent).
    e.g. "Z corrected ~18% vs E ~2%."
- **Reset** button.

## Configurability

- Master toggle (opt-in, default off).
- Strength slider (off → gentle tie-break → aggressive).
- Reset learned model.
- Honors incognito / no-learning fields.

## Implementation footprint

- **Gestures:** `ProximityInfo.createNativeProximityInfo` (`:185-198`) — add the
  learned per-key offset to `sweetSpotCenterXs/Ys` and scale `sweetSpotRadii`; when the
  feature is on, generate sweet spots from key centers even for layouts lacking
  `TouchPositionCorrection` data (so it doesn't depend on the layout shipping it).
  Re-push on keyboard reload / model update (learning is slow → no per-keystroke native
  churn). **No C++ rebuild.**
- **Taps:** `KeyDetector.detectHitKey` — distance to the learned center + the capped
  context prior tie-break.
- **Store:** new table in `leantype.db` + a DAO (follow `ClipboardDao`) + a
  `TouchModelManager` that computes capped effective geometry and applies the EMA
  update.
- **Learning hook:** record confident-tap offsets (incognito-gated) from the input
  path.
- **Settings:** 5-file pref pattern (toggle + strength), reset, stats page.

## Caveat to validate on-device

The default sweet spots are tuned. We must confirm the learned shifts *improve* gesture
recognition rather than destabilize it — hence confidence-gating, caps, opt-in, and the
stats page to keep it honest. Validate the gesture path explicitly (it's the priority).

## Phased build order

1. ✅ **Foundation:** opt-in pref (5-file) + `leantype.db` table + DAO + `TouchModelManager`
   (EMA update, capped effective-geometry API).
2. ✅ **Learning + gesture injection:** record letter taps; feed learned geometry into
   `ProximityInfo` sweet spots (gestures + tap-correction).
3. ✅ **Gesture-endpoint learning:** swipes teach the model via their start/end keys.
4. ✅ **Stats / "learned typing model" page** (`AdaptiveTypingStatsScreen`) + reset.
5. ✅ **Tap biasing + context prior (Layer A):** `KeyDetector` now biases the tapped key by
   the learned per-key offset AND a next-key prior. The prior is built in
   `AdaptiveKeyContext` from the top-5 suggestions, weighted **equally** (averaged, not
   score-skewed): the next char of the in-progress word's completions, or the first char of
   the next-word predictions for a fresh word. It is rebuilt between keystrokes (in
   `InputLogic.setSuggestedWords`, off the tap path) and read lock-free per tap. The prior's
   cap (`PRIOR_MAX_FRACTION` ≈ 18% of key) is deliberately a bit **below** the learned cap
   (≈ 25%), so it nudges rather than dominates. Bias is suppressed during gestures/swipes
   (`PointerTracker.isInGestureOrKeySwipe`) and only flips near-boundary taps. Gestures are
   intentionally NOT context-biased (suggestions don't change mid-swipe).
6. ⬜ **Strength/cap tuning** + optional heatmap visualization + interior-key gesture
   learning (needs corner-cutting handling or native alignment).

## Open questions (tracked)

1. Counts in backup: keep raw (slightly richer stats, faint usage signal) vs
   quantize/omit. Current plan: keep (needed for the stats page), local-only + opt-in.
2. Learning scope confirmed: per layout + orientation (not per size/one-handed/floating
   for v1).
3. Strength default and cap magnitude — tune on-device.
