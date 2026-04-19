package com.parc.fitnesstimer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parc.fitnesstimer.data.model.DisplayDigits
import com.parc.fitnesstimer.ui.theme.DisplayBlue
import com.parc.fitnesstimer.ui.theme.DisplayRed

// ── Segment bitmasks (bits 0-6 = A B C D E F G) ──────────────────────────────
//   A = top horiz, B = top-right vert, C = bot-right vert, D = bot horiz
//   E = bot-left vert, F = top-left vert, G = mid horiz
private val DIGIT_SEGMENTS = mapOf(
    0 to 0b0111111,
    1 to 0b0000110,
    2 to 0b1011011,
    3 to 0b1001111,
    4 to 0b1100110,
    5 to 0b1101101,
    6 to 0b1111101,
    7 to 0b0000111,
    8 to 0b1111111,
    9 to 0b1101111
)

/**
 * Draws a single 7-segment digit using Compose Canvas.
 * Inactive segments are rendered at low opacity (ghost segments).
 *
 * @param digit    0-9 for a visible digit; null for a blank/dark digit
 * @param color    Active segment colour
 * @param modifier Must specify a size (use [digitWidth])
 */
@Composable
fun SevenSegmentDigit(
    digit: Int?,
    color: Color,
    modifier: Modifier = Modifier,
    digitWidth: Dp = 44.dp
) {
    val segments = digit?.let { DIGIT_SEGMENTS[it] } ?: 0
    val inactiveColor = color.copy(alpha = 0.07f)

    Canvas(modifier = modifier
        .width(digitWidth)
        .height(digitWidth * 2f)) {

        val w = size.width
        val h = size.height
        val t  = w * 0.16f  // segment thickness
        val g  = w * 0.07f  // outer gap
        val ig = t * 0.65f  // inner gap — prevents corner overlap

        // Whether a given segment bit is active
        fun active(bit: Int) = (segments shr bit) and 1 == 1
        fun segColor(bit: Int) = if (active(bit)) color else inactiveColor

        // Horizontal bars (A=0, G=6, D=3)
        fun drawHSeg(topY: Float, bit: Int) {
            drawRoundRect(
                color      = segColor(bit),
                topLeft    = Offset(g + ig, topY),
                size       = Size(w - 2f * (g + ig), t),
                cornerRadius = CornerRadius(t / 2f)
            )
        }

        // Vertical bars
        fun drawVSeg(leftX: Float, fromY: Float, toY: Float, bit: Int) {
            drawRoundRect(
                color      = segColor(bit),
                topLeft    = Offset(leftX, fromY + ig),
                size       = Size(t, toY - fromY - 2f * ig),
                cornerRadius = CornerRadius(t / 2f)
            )
        }

        val mid    = h / 2f
        val leftX  = g
        val rightX = w - g - t

        drawHSeg(g, 0)                                  // A — top
        drawVSeg(rightX, g + t, mid, 1)                 // B — top-right
        drawVSeg(rightX, mid, h - g - t, 2)             // C — bot-right
        drawHSeg(h - g - t, 3)                          // D — bottom
        drawVSeg(leftX, mid, h - g - t, 4)              // E — bot-left
        drawVSeg(leftX, g + t, mid, 5)                  // F — top-left
        drawHSeg(mid - t / 2f, 6)                       // G — middle
    }
}

/**
 * Draws the colon separator (two dots) between D3 and D4.
 */
@Composable
fun SegmentColon(
    visible: Boolean,
    color: Color = DisplayRed,
    dotSize: Dp = 6.dp,
    modifier: Modifier = Modifier
) {
    val dotColor = if (visible) color else color.copy(alpha = 0.07f)
    Canvas(modifier = modifier
        .width(dotSize * 2)
        .height(dotSize * 8)) {
        val cx = size.width / 2f
        val spacing = size.height / 3f
        drawCircle(color = dotColor, radius = dotSize.toPx() / 2f,
            center = Offset(cx, spacing))
        drawCircle(color = dotColor, radius = dotSize.toPx() / 2f,
            center = Offset(cx, spacing * 2f))
    }
}

/**
 * Full 6-digit display panel: [D0 D1] [D2 D3 : D4 D5]
 *
 * D0/D1 = round counter in blue
 * D2/D3 = minutes in red
 * D4/D5 = seconds in red
 * Colon blink is driven by [DisplayDigits.colonOn]; the caller handles timing.
 */
@Composable
fun SixDigitDisplay(
    digits: DisplayDigits,
    digitWidth: Dp = 44.dp,
    modifier: Modifier = Modifier,
    flashOn: Boolean = true  // false during DONE state flash (dims all digits)
) {
    val blueAlpha  = if (flashOn) 1f else 0.06f
    val redAlpha   = if (flashOn) 1f else 0.06f
    val blueColor  = DisplayBlue.copy(alpha = blueAlpha)
    val redColor   = DisplayRed.copy(alpha = redAlpha)

    Row(
        modifier           = modifier,
        verticalAlignment  = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // ── Round counter (D0, D1) — blue ────────────────────────────────────
        SevenSegmentDigit(digit = digits.d0, color = blueColor, digitWidth = digitWidth)
        Spacer(Modifier.width(3.dp))
        SevenSegmentDigit(digit = digits.d1, color = blueColor, digitWidth = digitWidth)

        Spacer(Modifier.width(12.dp))

        // ── Timer minutes (D2, D3) — red ─────────────────────────────────────
        SevenSegmentDigit(digit = digits.d2, color = redColor, digitWidth = digitWidth)
        Spacer(Modifier.width(3.dp))
        SevenSegmentDigit(digit = digits.d3, color = redColor, digitWidth = digitWidth)

        // ── Colon ─────────────────────────────────────────────────────────────
        SegmentColon(visible = digits.colonOn && flashOn, color = redColor, dotSize = 7.dp)

        // ── Timer seconds (D4, D5) — red ─────────────────────────────────────
        SevenSegmentDigit(digit = digits.d4, color = redColor, digitWidth = digitWidth)
        Spacer(Modifier.width(3.dp))
        SevenSegmentDigit(digit = digits.d5, color = redColor, digitWidth = digitWidth)
    }
}
