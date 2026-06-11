// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.inputlogic

/**
 * Pure two-gate decision model for #24 "Assisted" spacing-policy tier.
 *
 * Stateless and Android-free: every input is explicit, so the whole thing is unit-testable
 * on the JVM without a running InputLogic.  Production wiring into [InputLogic.enterCombiningMode]
 * is intentionally deferred — this file only provides the model + trace carrier.
 *
 * Gate table (see docs/SPACING_POLICY.md §5):
 *
 * | Gate       | Condition                                       | Behaviour              |
 * |------------|-------------------------------------------------|------------------------|
 * | A — INSTANT | complete && prefixRichScore ≤ lowThreshold     | commit immediately     |
 * | B — PAUSE   | complete && prefixRichScore > lowThreshold     | wait for inter-word pause |
 * | NONE        | !complete  (or policy disabled)               | fall back to grace timer |
 */
object SpacingGateModel {

    /**
     * Which gate (if any) the current word falls into.
     *
     * - [NONE]    — policy disabled, or word not complete; fall back to signal-driven grace timer.
     * - [INSTANT] — Gate A: word is finished and prefix-richness is low → commit immediately.
     * - [PAUSE]   — Gate B: word is finished but prefix-richness is high → wait for the inter-word
     *               pause threshold before committing.
     */
    enum class Gate { NONE, INSTANT, PAUSE }

    /**
     * Decide which gate applies.
     *
     * @param policyEnabled   master "Assisted tier" switch (default off — see
     *                        [helium314.keyboard.latin.settings.Settings.PREF_SPACING_ASSISTED_TIER])
     * @param complete        the typed word is a real dictionary word (not user-typed-only)
     * @param prefixRichScore fraction of suggestion candidates that are completions [0..1]
     * @param lowThreshold    Gate-A ceiling: score ≤ this triggers [Gate.INSTANT]
     *                        (see [helium314.keyboard.latin.settings.Settings.PREF_SPACING_LOW_THRESHOLD])
     * @return the applicable gate, or [Gate.NONE] when conditions are not met
     */
    @JvmStatic
    fun decide(
        policyEnabled: Boolean,
        complete: Boolean,
        prefixRichScore: Float,
        lowThreshold: Float,
    ): Gate {
        if (!policyEnabled || !complete) return Gate.NONE
        return if (prefixRichScore <= lowThreshold) Gate.INSTANT else Gate.PAUSE
    }
}

/**
 * Immutable snapshot of the most recent gate evaluation, held by [InputLogic] as
 * `mLastSpacingGateDecision` for A11y / TraceRecorder / replay-harness consumption.
 *
 * The field is `@Nullable` so callers that have the Assisted tier disabled pay nothing —
 * no allocation until the policy is actually on.  Use [SpacingGateDecision.update] to
 * produce a fresh snapshot cheaply.
 */
data class SpacingGateDecision(
    /** The gate that fired (or [SpacingGateModel.Gate.NONE]). */
    val gate: SpacingGateModel.Gate,
    /** The `complete` signal at decision time. */
    val complete: Boolean,
    /** The `prefixRichScore` signal at decision time [0..1]. */
    val prefixRichScore: Float,
    /** The `lowThreshold` used at decision time. */
    val lowThreshold: Float,
) {
    companion object {
        /**
         * Convenience factory: evaluate [SpacingGateModel.decide] and wrap the result.
         * Called from [InputLogic.setSuggestedWords] when the Assisted tier is enabled.
         */
        @JvmStatic
        fun evaluate(
            policyEnabled: Boolean,
            complete: Boolean,
            prefixRichScore: Float,
            lowThreshold: Float,
        ): SpacingGateDecision = SpacingGateDecision(
            gate = SpacingGateModel.decide(policyEnabled, complete, prefixRichScore, lowThreshold),
            complete = complete,
            prefixRichScore = prefixRichScore,
            lowThreshold = lowThreshold,
        )
    }
}
