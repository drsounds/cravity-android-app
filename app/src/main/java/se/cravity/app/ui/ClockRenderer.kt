package se.cravity.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import se.cravity.app.model.ClockFace
import se.cravity.app.model.ClockMode
import se.cravity.app.model.HungerLevel
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the hunger clock with plain android.graphics so the same code renders
 * in the app (Compose native canvas) and in the home-screen widget (bitmap).
 */
object ClockRenderer {
    const val GREEN = 0xFF43A047.toInt()
    const val ORANGE = 0xFFFB8C00.toInt()
    const val RED = 0xFFE53935.toInt()

    fun colorOf(level: HungerLevel) = when (level) {
        HungerLevel.GREEN -> GREEN
        HungerLevel.ORANGE -> ORANGE
        HungerLevel.RED -> RED
    }

    fun bitmap(face: ClockFace, sizePx: Int, dark: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), sizePx.toFloat(), sizePx.toFloat(), face, dark)
        return bmp
    }

    fun draw(canvas: Canvas, width: Float, height: Float, face: ClockFace, dark: Boolean) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f * 0.96f
        val fg = if (dark) 0xFFECECEC.toInt() else 0xFF222222.toInt()
        val bg = if (dark) 0xFF1E1E1E.toInt() else 0xFFFAFAFA.toInt()
        val faint = if (dark) 0x33FFFFFF else 0x22000000

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Dial background
        paint.style = Paint.Style.FILL
        paint.color = bg
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.02f
        paint.color = fg
        canvas.drawCircle(cx, cy, r, paint)

        // Hunger zones as a ring of arcs
        val ringW = r * 0.16f
        val ringR = r * 0.80f
        val oval = RectF(cx - ringR, cy - ringR, cx + ringR, cy + ringR)
        paint.strokeWidth = ringW
        paint.strokeCap = Paint.Cap.BUTT
        paint.color = faint
        canvas.drawCircle(cx, cy, ringR, paint)
        val degPerMilli = 360.0 / face.spanMillis
        for (z in face.zones) {
            val start = face.angleOf(z.startMillis) - 90f
            val sweep = ((z.endMillis - z.startMillis) * degPerMilli).toFloat()
            paint.color = colorOf(z.level)
            canvas.drawArc(oval, start, sweep, false, paint)
        }

        // Ticks and numbers
        val hours = face.mode.hours
        paint.strokeWidth = r * 0.015f
        paint.color = fg
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textAlign = Paint.Align.CENTER
            textSize = if (face.mode == ClockMode.H12) r * 0.13f else r * 0.085f
            isFakeBoldText = true
        }
        for (h in 0 until hours) {
            val a = Math.toRadians(h * 360.0 / hours - 90.0)
            val outer = r * 0.97f
            val inner = if (face.mode == ClockMode.H24 && h % 6 != 0) r * 0.92f else r * 0.89f
            canvas.drawLine(
                cx + (cos(a) * inner).toFloat(), cy + (sin(a) * inner).toFloat(),
                cx + (cos(a) * outer).toFloat(), cy + (sin(a) * outer).toFloat(), paint,
            )
            val showLabel = face.mode == ClockMode.H12 || h % 2 == 0
            if (showLabel) {
                val label = if (face.mode == ClockMode.H12) (if (h == 0) 12 else h).toString() else h.toString()
                val lr = r * 0.56f
                val tx = cx + (cos(a) * lr).toFloat()
                val ty = cy + (sin(a) * lr).toFloat() - (text.descent() + text.ascent()) / 2f
                canvas.drawText(label, tx, ty, text)
            }
        }

        // Meal markers (planned meals inside the window)
        for (m in face.mealTimes) {
            val a = Math.toRadians(face.angleOf(m) - 90.0)
            val mx = cx + (cos(a) * ringR).toFloat()
            val my = cy + (sin(a) * ringR).toFloat()
            paint.style = Paint.Style.FILL
            paint.color = bg
            canvas.drawCircle(mx, my, ringW * 0.42f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = r * 0.02f
            paint.color = fg
            canvas.drawCircle(mx, my, ringW * 0.42f, paint)
        }

        // "Processed by" marker: a small triangle pointing at the ring from inside
        face.processedAtMillis?.let { p ->
            val a = Math.toRadians(face.angleOf(p) - 90.0)
            val tip = ringR - ringW * 0.55f
            val base = tip - ringW * 0.6f
            val spread = Math.toRadians(4.0)
            val path = android.graphics.Path().apply {
                moveTo(cx + (cos(a) * tip).toFloat(), cy + (sin(a) * tip).toFloat())
                lineTo(cx + (cos(a - spread) * base).toFloat(), cy + (sin(a - spread) * base).toFloat())
                lineTo(cx + (cos(a + spread) * base).toFloat(), cy + (sin(a + spread) * base).toFloat())
                close()
            }
            paint.style = Paint.Style.FILL
            paint.color = fg
            canvas.drawPath(path, paint)
        }

        // Hands
        val now = java.time.Instant.ofEpochMilli(face.nowMillis).atZone(face.zone)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = fg
        val hourA = Math.toRadians(face.angleOf(face.nowMillis) - 90.0)
        paint.strokeWidth = r * 0.055f
        val hourLen = if (face.mode == ClockMode.H24) r * 0.70f else r * 0.45f
        canvas.drawLine(cx, cy, cx + (cos(hourA) * hourLen).toFloat(), cy + (sin(hourA) * hourLen).toFloat(), paint)
        val minA = Math.toRadians(now.minute * 6.0 + now.second * 0.1 - 90.0)
        paint.strokeWidth = r * 0.03f
        val minLen = r * 0.68f
        if (face.mode == ClockMode.H12) {
            canvas.drawLine(cx, cy, cx + (cos(minA) * minLen).toFloat(), cy + (sin(minA) * minLen).toFloat(), paint)
        }
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, r * 0.05f, paint)
    }
}
