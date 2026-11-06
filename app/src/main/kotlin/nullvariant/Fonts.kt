package com.nullvariant.official

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat

object Fonts {

    fun applyMedieval(context: Context, root: View?) {
        val typeface = ResourcesCompat.getFont(context, R.font.medieval) ?: return

        fun walk(view: View) {
            if (view is TextView) view.typeface = typeface
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) walk(view.getChildAt(index))
            }
        }

        root?.let { walk(it) }
    }
}
