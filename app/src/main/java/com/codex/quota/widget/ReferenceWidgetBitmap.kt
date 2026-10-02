package com.codex.quota.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.codex.quota.domain.model.*
import com.codex.quota.ui.util.*
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.pow
import kotlin.math.sign

/** A single static draw per widget update: segmented bars need no animation or timers. */
internal object ReferenceWidgetBitmap {
    fun render(width: Float, height: Float, density: Float, fontScale: Float,
               data: AccountWithUsage?, palette: List<Int>, announcement: String?,
               roundedRegular: Typeface, roundedBold: Typeface): Bitmap {
        val pixelScale = density.coerceAtMost(3f)
        val bitmap = Bitmap.createBitmap((width * pixelScale).roundToInt().coerceAtLeast(1),
            (height * pixelScale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = (width / 330f).coerceIn(0.8f, 1.4f)
        canvas.scale(pixelScale * scale, pixelScale * scale)
        val w = width / scale
        val h = height / scale
        val fs = fontScale.coerceIn(1f, 1.3f)
        val tall = h >= 180f
        val compact = h < 140f
        val inset = if (compact) 18f else 22f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val background = palette[0]; val primary = palette[1]; val secondary = palette[2]
        val green = palette[4]; val blue = palette[5]
        val track = palette[6]; val error = palette[7]
        fun rect(x: Float, y: Float, right: Float, bottom: Float, color: Int, radius: Float = 0f) {
            paint.color = color
            canvas.drawRoundRect(x, y, right, bottom, radius, radius, paint)
        }
        fun text(value: String, x: Float, y: Float, size: Float, color: Int,
                 bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT, maxWidth: Float = w - x - inset,
                 shrink: Boolean = false) {
            paint.color = color
            paint.textSize = size * fs
            val latin = value.all { it.code <= 0x024F || it == '…' || it == '–' }
            paint.typeface = if (latin) {
                if (bold) roundedBold else roundedRegular
            } else Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            paint.textAlign = align
            var label = value
            if (shrink && paint.measureText(label) > maxWidth)
                paint.textSize *= (maxWidth / paint.measureText(label) * .98f).coerceAtLeast(.60f)
            if (paint.measureText(label) > maxWidth) {
                while (label.isNotEmpty() && paint.measureText(label + "…") > maxWidth) label = label.dropLast(1)
                label += "…"
            }
            canvas.drawText(label, x, y, paint)
        }
        // Superellipse corners ease into the straight sides instead of abruptly joining a circular arc.
        val radius = minOf(w * .14f, h * .30f, 48f)
        val outline = Path()
        outline.moveTo(radius, 0f)
        fun corner(cx: Float, cy: Float, start: Double) {
            for (step in 1..32) {
                val angle = start + step * Math.PI / 64
                val x = cos(angle); val y = sin(angle)
                outline.lineTo(cx + radius * (sign(x) * abs(x).pow(2.0 / 3)).toFloat(),
                    cy + radius * (sign(y) * abs(y).pow(2.0 / 3)).toFloat())
            }
        }
        outline.lineTo(w - radius, 0f)
        corner(w - radius, radius, -Math.PI / 2)
        outline.lineTo(w, h - radius)
        corner(w - radius, h - radius, 0.0)
        outline.lineTo(radius, h)
        corner(radius, h - radius, Math.PI / 2)
        outline.lineTo(0f, radius)
        corner(radius, radius, Math.PI)
        outline.close()
        paint.color = background
        canvas.drawPath(outline, paint)
        val headerBaseline = if (compact) 26f else 33f
        text("Codex", inset, headerBaseline, if (compact) 16f else 18f, primary, bold = true)
        if (data == null) {
            text("点击连接官网账户", inset, h / 2 + 10, 12f, secondary)
            return bitmap
        }
        val usage = data.usage
        val updated = phoneTime(data.account.lastSuccessfulSyncEpochMs).substringAfter(' ')
        val plan = data.account.planType.displayName.removePrefix("ChatGPT ")
        text("$plan · $updated", w - 44, headerBaseline - 1, 9f, secondary, align = Paint.Align.RIGHT, maxWidth = w * .48f)
        val bodyTop = if (tall) 55f else if (compact) 36f else 46f
        val rowHeight = ((h - bodyTop - 28f) / 2).coerceAtMost(60f)
        val left = inset
        val split = w * .58f
        val leftWidth = split - left - 14f
        val right = split + 16f
        val rightWidth = w - right - inset
        fun quota(label: String, percent: Double?, reset: Long?, top: Float, accent: Int) {
            val baseline = top + if (tall) 15 else 12
            text(label, left, baseline, if (tall) 11.5f else 10.5f, secondary, maxWidth = leftWidth * .5f)
            text(phonePercent(percent), left + leftWidth, baseline, if (compact) 14f else 18f, accent,
                bold = true, align = Paint.Align.RIGHT, maxWidth = leftWidth * .5f, shrink = true)
            val y = top + if (tall) 26 else 19
            val gap = 3f
            val segment = (leftWidth - 13 * gap) / 14
            val progress = percent?.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0)?.times(.14)
            val rowTrack = if (accent == green) 0xFF17241A.toInt() else 0xFF17243B.toInt()
            repeat(14) { i ->
                val x = left + i * (segment + gap)
                rect(x, y, x + segment, y + 6, rowTrack, 2.4f)
                val fill = progress?.minus(i)?.coerceIn(0.0, 1.0)?.toFloat() ?: 0f
                if (fill > 0) rect(x, y, x + segment * fill, y + 6, accent, 2.4f)
            }
            if (rowHeight >= if (fs > 1.15f) 42 else 36)
                text("重置 ${phoneTime(reset)}", left, top + if (tall) 46 else if (fs > 1.15f) 40 else 36,
                    if (tall) 9.5f else 9f, secondary, maxWidth = leftWidth, shrink = true)
        }
        quota("5 小时", usage?.fiveHourRemainingPercent, usage?.fiveHourResetAtEpochMs, bodyTop, green)
        quota("7 天", usage?.remainingPercent, usage?.resetAtEpochMs, bodyTop + rowHeight, blue)
        rect(split, bodyTop + 4, split + .5f, bodyTop + rowHeight + if (tall) 46 else 27, track)
        text("累计 Token", right, bodyTop + if (tall) 15 else 12, 10f, secondary, maxWidth = rightWidth)
        val tokenText = usage?.usedTokens?.let {
            if (it >= 1_000_000) String.format(Locale.CHINA, "%.1fM", it / 1_000_000.0) else phoneTokens(it)
        } ?: "不可用"
        text(tokenText, right, bodyTop + if (tall) 46 else 39, if (usage?.usedTokens == null) 15f else if (compact) 21f else 24f,
            primary, bold = true, maxWidth = rightWidth, shrink = true)
        val history = usage?.activityDailyUsage.orEmpty().ifEmpty {
            val date = usage?.activityDate
            val tokens = usage?.activityDateTokens
            if (date != null && tokens != null) listOf(DailyTokenUsage(date, tokens)) else emptyList()
        }
        val latest = history.maxByOrNull { it.date }
        val chartBottom = minOf(h - 46, bodyTop + 105)
        val chartTop = maxOf(bodyTop + if (tall) 62 else 51, chartBottom - 34)
        if (latest != null && chartBottom > chartTop + 8 && fs <= 1.15f) {
            // Anchor to the API's last reported date, never relabel an old bucket as today.
            val lastDate = runCatching { LocalDate.parse(latest.date) }.getOrNull()
            val days = if (lastDate == null) emptyList() else (6 downTo 0).map { lastDate.minusDays(it.toLong()).toString() }
            val indexed = history.associateBy { it.date }
            val max = days.mapNotNull { indexed[it]?.tokens }.maxOrNull()?.coerceAtLeast(1) ?: 1L
            val gap = 4f
            val barWidth = (rightWidth - gap * 6) / 7
            days.forEachIndexed { i, date ->
                val x = right + i * (barWidth + gap)
                rect(x, chartBottom - 1, x + barWidth, chartBottom, track)
                indexed[date]?.tokens?.let { tokens ->
                    if (tokens > 0) {
                        val barHeight = ((tokens.toDouble() / max) * (chartBottom - chartTop)).toFloat().coerceAtLeast(2f)
                        rect(x, chartBottom - barHeight, x + barWidth, chartBottom, green, 2f)
                    }
                }
            }
            text("日用量 · ${latest.date.substring(5).replace('-', '/')}", right, chartBottom + 14,
                8f, secondary, maxWidth = rightWidth)
        } else if (h >= 150) {
            text(if (latest == null) "活动数据暂不可用" else "最近 ${latest.date.substring(5)}",
                right, chartBottom + 14, 8f, secondary, maxWidth = rightWidth)
        }
        val status = usage?.status ?: data.account.authStatus
        val label = when (status) {
            AuthStatus.AUTHENTICATED -> "更新 ${phoneTime(data.account.lastSuccessfulSyncEpochMs)}"
            AuthStatus.AUTHENTICATION_REQUIRED -> "登录失效 · 点击重新授权"
            AuthStatus.OFFLINE -> "离线缓存 ${phoneTime(data.account.lastSuccessfulSyncEpochMs)}"
            else -> "查询失败 · ${phoneTime(data.account.lastSuccessfulSyncEpochMs)}"
        }
        val showAnnouncement = announcement != null && h >= 153 && fs <= 1.15f && status == AuthStatus.AUTHENTICATED
        val footerLabel = if (showAnnouncement) "社区预告 · $announcement" else label
        val footerColor = if (status.isError) error else if (showAnnouncement) blue else secondary
        val footerBaseline = h - if (compact) 12 else 17
        rect(inset, footerBaseline - 5, inset + 4, footerBaseline - 1, footerColor, 2f)
        text(footerLabel, inset + 9, footerBaseline, if (compact) 7.5f else 8.5f, footerColor, shrink = true)
        return bitmap
    }
}
