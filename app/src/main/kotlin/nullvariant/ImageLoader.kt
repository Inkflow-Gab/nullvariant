package com.nullvariant.official

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import androidx.annotation.DrawableRes

object ImageLoader {

    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(view: ImageView, @DrawableRes resId: Int, targetPx: Int) {
        val target = targetPx.coerceAtLeast(1)
        val key = "$resId@$target"
        cache.get(key)?.let {
            view.setImageBitmap(it)
            return
        }
        val bitmap = decode(view.context, resId, target) ?: return
        cache.put(key, bitmap)
        view.setImageBitmap(bitmap)
    }

    private fun decode(context: Context, @DrawableRes resId: Int, targetPx: Int): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(context.resources, resId, bounds)

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                null
            } else {
                var sampleSize = 1
                val largest = maxOf(bounds.outWidth, bounds.outHeight)
                while (largest / (sampleSize * 2) >= targetPx) {
                    sampleSize *= 2
                }

                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeResource(context.resources, resId, options)
            }
        }.getOrNull()
    }
}
