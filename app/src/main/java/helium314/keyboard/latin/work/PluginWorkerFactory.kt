// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
package helium314.keyboard.latin.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import helium314.keyboard.latin.utils.Log

class PluginRuntime(
    val classLoader: ClassLoader,
    val workerContext: Context,
    private val initializer: (() -> Unit)? = null
) {
    @Volatile
    private var initialized = false

    @Synchronized
    fun ensureInitialized() {
        if (!initialized) {
            try {
                initializer?.invoke()
            } catch (e: Throwable) {
                Log.e("PluginRuntime", "Error during lazy plugin runtime initialization", e)
            }
            initialized = true
        }
    }
}

class PluginWorkerFactory : WorkerFactory() {

    @Volatile
    var pluginRuntime: PluginRuntime? = null

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        val runtime = this.pluginRuntime

        val hostCanLoad = try {
            appContext.classLoader.loadClass(workerClassName)
            true
        } catch (_: ClassNotFoundException) {
            false
        }

        if (runtime != null && (!hostCanLoad || workerClassName.startsWith("com.google.mlkit."))) {
            runtime.ensureInitialized()
            instantiate(
                classLoader = runtime.classLoader,
                context = runtime.workerContext,
                workerClassName = workerClassName,
                params = workerParameters
            )?.let { return it }
        }

        return instantiate(
            classLoader = appContext.classLoader,
            context = appContext,
            workerClassName = workerClassName,
            params = workerParameters
        )
    }

    private fun instantiate(
        classLoader: ClassLoader,
        context: Context,
        workerClassName: String,
        params: WorkerParameters
    ): ListenableWorker? {
        return try {
            val clazz = classLoader.loadClass(workerClassName)
            if (!ListenableWorker::class.java.isAssignableFrom(clazz)) {
                return null
            }
            val constructor = clazz.getDeclaredConstructor(
                Context::class.java,
                WorkerParameters::class.java
            )
            constructor.isAccessible = true
            constructor.newInstance(context, params) as? ListenableWorker
        } catch (_: Throwable) {
            null
        }
    }
}
