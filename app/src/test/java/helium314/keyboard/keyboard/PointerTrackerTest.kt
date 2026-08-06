package helium314.keyboard.keyboard

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PointerTrackerTest {
    @Test
    fun cancelAllPointerTrackersAfterViewDataClearedDoesNotCrash() {
        PointerTracker.clearOldViewData()

        PointerTracker.cancelAllPointerTrackers()
    }
}
