package com.bitcoinprice.app

import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Line chart of BTC/USD price. Long ranges (e.g. full history since 2010) read
 * better on a log scale, since a linear one flattens every year before ~2020
 * given the price range from cents to tens of thousands; short ranges (e.g.
 * the last 6 months) use a linear scale instead. Tap or drag on the curve to
 * see the date and price at that point.
 */
@Composable
fun PriceHistoryChart(
    points: List<PricePoint>,
    modifier: Modifier = Modifier,
    useLogScale: Boolean = true,
    heightDp: Dp = 260.dp
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tooltipBg = MaterialTheme.colorScheme.inverseSurface
    val tooltipTextColor = MaterialTheme.colorScheme.inverseOnSurface

    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }

    val density = LocalDensity.current
    // Reserve just enough room on the left for the widest price label, so it
    // never overlaps the plotted line (measured once, shared by touch + draw).
    val leftPadPx = remember(points, useLogScale, density) {
        computeLeftAxisPadding(points, useLogScale, density)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .pointerInput(points, leftPadPx) {
                if (points.size < 2) return@pointerInput
                val leftPad = leftPadPx
                val rightPad = 8.dp.toPx()
                val chartWidth = size.width - leftPad - rightPad
                val minTime = points.first().timeSec
                val maxTime = points.last().timeSec
                val timeSpan = max(1L, maxTime - minTime)

                fun indexForX(x: Float): Int {
                    val frac = ((x - leftPad) / chartWidth).coerceIn(0f, 1f)
                    val targetTime = minTime + (frac * timeSpan).toLong()
                    var lo = 0
                    var hi = points.size - 1
                    while (lo < hi) {
                        val mid = (lo + hi) / 2
                        if (points[mid].timeSec < targetTime) lo = mid + 1 else hi = mid
                    }
                    if (lo > 0 &&
                        abs(points[lo - 1].timeSec - targetTime) <= abs(points[lo].timeSec - targetTime)
                    ) {
                        lo -= 1
                    }
                    return lo
                }

                awaitEachGesture {
                    val firstEvent = awaitPointerEvent()
                    val down = firstEvent.changes.firstOrNull { it.pressed } ?: return@awaitEachGesture
                    selectedIndex = indexForX(down.position.x)
                    down.consume()
                    val pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break
                        selectedIndex = indexForX(change.position.x)
                        change.consume()
                    }
                }
            }
    ) {
        if (points.size < 2) return@Canvas

        val leftPad = leftPadPx
        val labelStartX = 2.dp.toPx()
        val bottomPad = 24.dp.toPx()
        val topPad = 8.dp.toPx()
        val rightPad = 8.dp.toPx()

        val chartWidth = size.width - leftPad - rightPad
        val chartHeight = size.height - topPad - bottomPad

        val minTime = points.first().timeSec
        val maxTime = points.last().timeSec
        val timeSpan = max(1L, maxTime - minTime)

        fun toScale(price: Double): Double = if (useLogScale) ln(max(price, 0.0001)) else price
        fun fromScale(value: Double): Double = if (useLogScale) exp(value) else value

        val scaledValues = points.map { toScale(it.price) }
        val minV = scaledValues.min()
        val maxV = scaledValues.max()
        val valueSpan = (maxV - minV).let { if (it < 1e-9) 1.0 else it }

        fun xFor(timeSec: Long): Float =
            leftPad + (chartWidth * (timeSec - minTime).toFloat() / timeSpan)

        fun yFor(value: Double): Float =
            topPad + chartHeight - (chartHeight * ((value - minV) / valueSpan)).toFloat()

        val labelPaint = AndroidPaint().apply {
            color = labelColor.toArgbCompat()
            textSize = 10.sp.toPx()
            isAntiAlias = true
        }
        val tickLabelPaint = AndroidPaint(labelPaint).apply {
            textSize = 8.sp.toPx()
            textAlign = AndroidPaint.Align.CENTER
        }

        // Horizontal grid lines with price labels (labels sit left of the
        // plot area, in the reserved leftPad margin, never over the line).
        val gridLines = 4
        for (i in 0..gridLines) {
            val frac = i.toDouble() / gridLines
            val value = minV + frac * valueSpan
            val y = yFor(value)
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(size.width - rightPad, y),
                strokeWidth = 1f
            )
            drawContext.canvas.nativeCanvas.drawText(
                formatPriceShort(fromScale(value)),
                labelStartX,
                (y - 4.dp.toPx()).coerceAtLeast(topPad + 10f),
                labelPaint
            )
        }

        // Vertical grid lines and date labels on the x-axis.
        for ((t, label) in computeXAxisTicks(minTime, maxTime)) {
            val x = xFor(t)
            drawLine(
                color = gridColor,
                start = Offset(x, topPad),
                end = Offset(x, size.height - bottomPad),
                strokeWidth = 1f
            )
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x,
                size.height - 6.dp.toPx(),
                tickLabelPaint
            )
        }

        // Price line.
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = xFor(point.timeSec)
            val y = yFor(toScale(point.price))
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 2.5.dp.toPx()))

        // Selection: vertical guide line, highlighted point and a date/price tooltip.
        val selected = selectedIndex?.let { points.getOrNull(it) }
        if (selected != null) {
            val selX = xFor(selected.timeSec)
            val selY = yFor(toScale(selected.price))

            drawLine(
                color = labelColor,
                start = Offset(selX, topPad),
                end = Offset(selX, size.height - bottomPad),
                strokeWidth = 1f
            )
            drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(selX, selY))
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = Offset(selX, selY)
            )

            val dateText = formatSelectedDate(selected.timeSec)
            val priceText = formatSelectedPrice(selected.price)

            val valuePaint = AndroidPaint().apply {
                color = tooltipTextColor.toArgbCompat()
                textSize = 13.sp.toPx()
                isAntiAlias = true
                isFakeBoldText = true
            }
            val datePaint = AndroidPaint(valuePaint).apply {
                textSize = 10.sp.toPx()
                isFakeBoldText = false
            }

            val textWidth = max(valuePaint.measureText(priceText), datePaint.measureText(dateText))
            val boxPaddingH = 10.dp.toPx()
            val boxPaddingV = 8.dp.toPx()
            val lineGap = 2.dp.toPx()
            val boxWidth = textWidth + boxPaddingH * 2
            val boxHeight = valuePaint.textSize + datePaint.textSize + lineGap + boxPaddingV * 2

            val gap = 10.dp.toPx()
            val boxAbove = selY - gap - boxHeight >= topPad
            val boxTop = if (boxAbove) selY - gap - boxHeight else selY + gap
            val maxBoxLeft = (size.width - rightPad - boxWidth).coerceAtLeast(leftPad)
            val boxLeft = (selX - boxWidth / 2).coerceIn(leftPad, maxBoxLeft)

            drawRoundRect(
                color = tooltipBg,
                topLeft = Offset(boxLeft, boxTop),
                size = androidx.compose.ui.geometry.Size(boxWidth, boxHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )

            val centerX = boxLeft + boxWidth / 2
            val datePaintCentered = AndroidPaint(datePaint).apply { textAlign = AndroidPaint.Align.CENTER }
            val valuePaintCentered = AndroidPaint(valuePaint).apply { textAlign = AndroidPaint.Align.CENTER }
            drawContext.canvas.nativeCanvas.drawText(
                dateText,
                centerX,
                boxTop + boxPaddingV + datePaint.textSize - 2f,
                datePaintCentered
            )
            drawContext.canvas.nativeCanvas.drawText(
                priceText,
                centerX,
                boxTop + boxPaddingV + datePaint.textSize + lineGap + valuePaint.textSize - 2f,
                valuePaintCentered
            )
        }
    }
}

/** Widest y-axis price label plus a small gap, so the plot never starts under the text. */
private fun computeLeftAxisPadding(points: List<PricePoint>, useLogScale: Boolean, density: Density): Float {
    val minGap = with(density) { 8.dp.toPx() }
    if (points.size < 2) return minGap

    fun toScale(price: Double) = if (useLogScale) ln(max(price, 0.0001)) else price
    fun fromScale(value: Double) = if (useLogScale) exp(value) else value

    val scaledValues = points.map { toScale(it.price) }
    val minV = scaledValues.min()
    val maxV = scaledValues.max()
    val valueSpan = (maxV - minV).let { if (it < 1e-9) 1.0 else it }

    val paint = AndroidPaint().apply { textSize = with(density) { 10.sp.toPx() } }
    val gridLines = 4
    var maxLabelWidth = 0f
    for (i in 0..gridLines) {
        val value = minV + (i.toDouble() / gridLines) * valueSpan
        val label = formatPriceShort(fromScale(value))
        maxLabelWidth = max(maxLabelWidth, paint.measureText(label))
    }
    return maxLabelWidth + with(density) { 8.dp.toPx() }
}

/**
 * Year ticks for long ranges, month ticks for short ones (under ~1.5 years)
 * so the 6-month chart doesn't try to draw year gridlines.
 */
private fun computeXAxisTicks(minTime: Long, maxTime: Long): List<Pair<Long, String>> {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    val ticks = mutableListOf<Pair<Long, String>>()
    val spanDays = (maxTime - minTime) / 86400

    if (spanDays > 550) {
        calendar.timeInMillis = minTime * 1000
        val startYear = calendar.get(Calendar.YEAR)
        calendar.timeInMillis = maxTime * 1000
        val endYear = calendar.get(Calendar.YEAR)

        var year = startYear
        while (year <= endYear) {
            calendar.set(year, Calendar.JANUARY, 1, 0, 0, 0)
            val t = calendar.timeInMillis / 1000
            if (t in minTime..maxTime) ticks.add(t to year.toString())
            year += 1
        }
    } else {
        val monthFormat = SimpleDateFormat("MMM", Locale("ru")).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        calendar.timeInMillis = minTime * 1000
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        if (calendar.timeInMillis / 1000 < minTime) calendar.add(Calendar.MONTH, 1)

        while (calendar.timeInMillis / 1000 <= maxTime) {
            val t = calendar.timeInMillis / 1000
            val label = monthFormat.format(calendar.time).replaceFirstChar { it.uppercase() }
            ticks.add(t to label)
            calendar.add(Calendar.MONTH, 1)
        }
    }
    return ticks
}

private fun formatPriceShort(price: Double): String = when {
    price >= 1000 -> "$" + "%,.0f".format(price)
    price >= 1 -> "$" + "%.1f".format(price)
    else -> "$" + "%.3f".format(price)
}

private fun formatSelectedPrice(price: Double): String = when {
    price >= 1 -> "$" + "%,.2f".format(price)
    else -> "$" + "%.4f".format(price)
}

private val selectedDateFormat = SimpleDateFormat("d MMM yyyy", Locale("ru")).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

private fun formatSelectedDate(timeSec: Long): String = selectedDateFormat.format(timeSec * 1000)

private fun Color.toArgbCompat(): Int {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
