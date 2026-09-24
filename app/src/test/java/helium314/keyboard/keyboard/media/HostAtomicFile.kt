// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.util.AtomicFile
import android.util.Log
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Android's File.renameTo replaces an existing file; Windows' implementation does not.
 * Adapt only AtomicFile's private rename primitive, preserving real streams, sync, rollback,
 * reads, and the cache's readback verification. This is not on-device durability evidence.
 */
@Implements(AtomicFile::class)
class HostAtomicFile {
    companion object {
        @JvmStatic
        @Implementation
        fun rename(source: File, target: File) {
            try {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (_: IOException) {
                Log.e("AtomicFile", "Failed to rename $source to $target")
            }
        }
    }
}
