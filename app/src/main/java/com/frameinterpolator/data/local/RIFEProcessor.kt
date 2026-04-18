package com.frameinterpolator.data.local

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap

class RIFEProcessor(
    context: Context
) {
    private val assetManager: AssetManager = context.applicationContext.assets
    @Volatile
    private var initialized = false

    fun ensureInitialized(): Boolean = synchronized(this) {
        if (initialized) {
            return@synchronized true
        }

        initialized = nativeInit(assetManager, -1)
        initialized
    }

    fun interpolate(
        firstFrame: Bitmap,
        secondFrame: Bitmap,
        timestep: Float
    ): Bitmap? {
        if (!initialized && !ensureInitialized()) {
            return null
        }
        return nativeInterpolate(firstFrame, secondFrame, timestep)
    }

    fun version(): String = nativeGetVersion()

    fun destroy() = synchronized(this) {
        if (initialized) {
            nativeDestroy()
            initialized = false
        }
    }

    private external fun nativeInit(assetManager: AssetManager, gpuId: Int): Boolean
    private external fun nativeInterpolate(bitmap0: Bitmap, bitmap1: Bitmap, timestep: Float): Bitmap?
    private external fun nativeGetVersion(): String
    private external fun nativeDestroy()

    companion object {
        init {
            System.loadLibrary("rife_processor")
        }
    }
}
