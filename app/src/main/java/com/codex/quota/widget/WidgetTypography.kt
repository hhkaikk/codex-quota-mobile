package com.codex.quota.widget

import android.content.Context
import android.graphics.Typeface

/** Bundled OFL font; loaded once per process, without a font provider or network request. */
internal object WidgetTypography {
    @Volatile private var cached: Pair<Typeface, Typeface>? = null

    fun rounded(context: Context): Pair<Typeface, Typeface> = cached ?: synchronized(this) {
        cached ?: Pair(
            Typeface.Builder(context.assets, "fonts/Nunito.ttf")
                .setFontVariationSettings("'wght' 600").build(),
            Typeface.Builder(context.assets, "fonts/Nunito.ttf")
                .setFontVariationSettings("'wght' 750").build()
        ).also { cached = it }
    }
}
