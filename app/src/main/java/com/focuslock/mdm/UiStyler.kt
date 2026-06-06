package com.focuslock.mdm

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

object UiStyler {

    fun applyWallpaperOrColor(root: View, theme: UiPrefs.UiTheme, wallpaper: UiPrefs.UiWallpaper) {
        if (wallpaper.drawableRes != 0) {
            root.setBackgroundResource(wallpaper.drawableRes)
        } else {
            root.setBackgroundColor(theme.background)
        }
    }

    fun applyCardBackground(view: View, color: Int) {
        val bg = view.background
        if (bg is GradientDrawable) {
            bg.mutate()
            bg.setColor(color)
        } else {
            view.setBackgroundColor(color)
        }
    }

    fun applyCardRadius(view: View, radiusPx: Float) {
        val bg = view.background
        if (bg is GradientDrawable) {
            bg.mutate()
            bg.cornerRadius = radiusPx
        }
    }

    fun applyTypefaceRecursive(view: View, typeface: Typeface) {
        if (view is TextView) {
            view.typeface = typeface
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyTypefaceRecursive(view.getChildAt(i), typeface)
            }
        }
    }

    fun dpToPx(context: Context, dp: Int): Int {
        val scale = context.resources.displayMetrics.density
        return (dp * scale).toInt()
    }
}
