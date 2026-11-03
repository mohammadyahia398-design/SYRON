package com.syron.app.core.glass

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syron.app.core.theme.SyronColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class GlassIcon { Back, Send, Stop, Gear, Chat, Trash, Check }

/** نقرة بلا موجة ضغط افتراضية (الضغط يُعبَّر عنه بنابض في الحجم). */
fun Modifier.plainClick(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

@Composable
fun GlassIconButton(
    icon: GlassIcon,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 48.dp,
    accent: Boolean = false,
    enabled: Boolean = true
) {
    val env = LocalGlassEnv.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val spec: AnimationSpec<Float> =
        if (env.reduceMotion) snap() else spring(dampingRatio = 0.45f, stiffness = 380f)
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, spec, label = "iconBtnScale")
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val iconColor = if (accent) SyronColors.Cyan else SyronColors.Text

    GlassSurface(
        modifier = modifier
            .size(diameter)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.4f
            }
            .semantics { contentDescription = description }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        cornerRadius = diameter / 2,
        tint = if (accent) SyronColors.Cyan.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f),
        borderAlpha = if (accent) 0.6f else 0.45f
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(diameter * 0.28f)
        ) {
            drawGlassIcon(icon, iconColor, rtl)
        }
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true
,
    refract: Boolean = true
) {
    val env = LocalGlassEnv.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val spec: AnimationSpec<Float> =
        if (env.reduceMotion) snap() else spring(dampingRatio = 0.45f, stiffness = 380f)
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spec, label = "btnScale")

    GlassSurface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.4f
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        cornerRadius = 24.dp,
        refract = refract,
        tint = if (accent) SyronColors.Cyan.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f),
        borderAlpha = if (accent) 0.6f else 0.45f
    ) {
        Text(
            text = text,
            color = if (accent) SyronColors.Cyan else SyronColors.Text,
            fontSize = 15.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(PaddingValues(horizontal = 22.dp, vertical = 12.dp))
        )
    }
}

@Composable
fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    password: Boolean = false,
    ltr: Boolean = false
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = TextStyle(
            color = SyronColors.Text,
            fontSize = 16.sp,
            textDirection = if (ltr) TextDirection.Ltr else TextDirection.Unspecified
        ),
        cursorBrush = SolidColor(SyronColors.Cyan),
        singleLine = singleLine,
        maxLines = maxLines,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Text
        ),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(text = placeholder, color = SyronColors.TextDim, fontSize = 16.sp)
                }
                inner()
            }
        }
    )
}

internal fun DrawScope.drawGlassIcon(icon: GlassIcon, color: Color, rtl: Boolean) {
    val w = size.width
    val h = size.height
    val sw = (w * 0.11f).coerceAtLeast(1.6f)
    val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun fx(x: Float) = if (rtl) w - x else x
    when (icon) {
        GlassIcon.Back -> {
            val p = Path().apply {
                moveTo(fx(w * 0.7f), h * 0.12f)
                lineTo(fx(w * 0.3f), h * 0.5f)
                lineTo(fx(w * 0.7f), h * 0.88f)
            }
            drawPath(p, color, style = stroke)
        }
        GlassIcon.Send -> {
            drawLine(color, Offset(w * 0.5f, h * 0.88f), Offset(w * 0.5f, h * 0.14f), sw, StrokeCap.Round)
            val p = Path().apply {
                moveTo(w * 0.18f, h * 0.44f)
                lineTo(w * 0.5f, h * 0.12f)
                lineTo(w * 0.82f, h * 0.44f)
            }
            drawPath(p, color, style = stroke)
        }
        GlassIcon.Stop -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.16f, h * 0.16f),
                size = Size(w * 0.68f, h * 0.68f),
                cornerRadius = CornerRadius(w * 0.16f)
            )
        }
        GlassIcon.Gear -> {
            drawCircle(color, radius = w * 0.2f, center = center, style = stroke)
            for (i in 0 until 8) {
                val a = (i * 45f) * (PI.toFloat() / 180f)
                val c = cos(a)
                val s = sin(a)
                drawLine(
                    color,
                    Offset(center.x + c * w * 0.34f, center.y + s * w * 0.34f),
                    Offset(center.x + c * w * 0.47f, center.y + s * w * 0.47f),
                    sw,
                    StrokeCap.Round
                )
            }
        }
        GlassIcon.Chat -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.08f, h * 0.12f),
                size = Size(w * 0.84f, h * 0.6f),
                cornerRadius = CornerRadius(w * 0.2f),
                style = stroke
            )
            val p = Path().apply {
                moveTo(w * 0.3f, h * 0.72f)
                lineTo(w * 0.24f, h * 0.92f)
                lineTo(w * 0.48f, h * 0.72f)
            }
            drawPath(p, color, style = stroke)
        }
        GlassIcon.Trash -> {
            drawLine(color, Offset(w * 0.12f, h * 0.26f), Offset(w * 0.88f, h * 0.26f), sw, StrokeCap.Round)
            val handle = Path().apply {
                moveTo(w * 0.38f, h * 0.26f)
                lineTo(w * 0.38f, h * 0.1f)
                lineTo(w * 0.62f, h * 0.1f)
                lineTo(w * 0.62f, h * 0.26f)
            }
            drawPath(handle, color, style = stroke)
            val body = Path().apply {
                moveTo(w * 0.24f, h * 0.26f)
                lineTo(w * 0.3f, h * 0.9f)
                lineTo(w * 0.7f, h * 0.9f)
                lineTo(w * 0.76f, h * 0.26f)
            }
            drawPath(body, color, style = stroke)
        }
        GlassIcon.Check -> {
            val p = Path().apply {
                moveTo(w * 0.15f, h * 0.55f)
                lineTo(w * 0.4f, h * 0.78f)
                lineTo(w * 0.85f, h * 0.22f)
            }
            drawPath(p, color, style = stroke)
        }
    }
}
