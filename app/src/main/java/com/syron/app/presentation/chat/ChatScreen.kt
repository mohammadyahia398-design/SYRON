package com.syron.app.presentation.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syron.app.core.glass.GlassButton
import com.syron.app.core.glass.GlassField
import com.syron.app.core.glass.GlassIcon
import com.syron.app.core.glass.GlassIconButton
import com.syron.app.core.glass.GlassPanel
import com.syron.app.core.glass.GlassSurface
import com.syron.app.core.glass.LocalGlassEnv
import com.syron.app.core.theme.SyronColors
import com.syron.app.domain.model.ChatMsg
import com.syron.app.presentation.orb.SyronOrb
import com.syron.app.presentation.settings.SettingsViewModel
import kotlin.math.abs
import kotlin.math.min

@Composable
fun ChatScreen(
    vm: ChatViewModel,
    settings: SettingsViewModel,
    onBack: () -> Unit,
    onSettings: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // القائمة معكوسة: العنصر 0 هو الأحدث وهو في الأسفل، فيبقى الرد المتدفق ثابتاً عند القاع
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        TopBar(vm, settings, onBack, onSettings)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (vm.messages.isEmpty()) {
                EmptyState(settings, onSettings)
            } else {
                // اتجاه ثابت LTR: المستخدم يمين دائماً والمساعد يسار، مهما كانت لغة النظام
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items = vm.messages.asReversed(), key = { it.id }) { m ->
                            Bubble(m)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            GlassSurface(Modifier.weight(1f), cornerRadius = 26.dp) {
                GlassField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = "اكتب رسالتك…",
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            SendStop(
                generating = vm.generating,
                canSend = input.isNotBlank(),
                onSend = {
                    vm.send(input)
                    input = ""
                },
                onStop = { vm.stop() }
            )
        }
    }
}

@Composable
private fun TopBar(
    vm: ChatViewModel,
    settings: SettingsViewModel,
    onBack: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconButton(GlassIcon.Back, "رجوع", onBack, diameter = 44.dp)
        Spacer(Modifier.width(8.dp))
        GlassSurface(
            modifier = Modifier.weight(1f).height(44.dp),
            cornerRadius = 22.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SyronOrb(Modifier.size(34.dp), 34.dp, false, vm.generating)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "SYRON · ${settings.personality.name}",
                    color = SyronColors.Text,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        GlassIconButton(GlassIcon.Trash, "مسح المحادثة", { vm.clearChat() }, diameter = 44.dp)
        Spacer(Modifier.width(8.dp))
        GlassIconButton(GlassIcon.Sliders, "الإعدادات", onSettings, diameter = 44.dp)
    }
}

@Composable
private fun SendStop(generating: Boolean, canSend: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    val reduce = LocalGlassEnv.current.reduceMotion
    AnimatedContent(
        targetState = generating,
        modifier = Modifier.size(48.dp),
        transitionSpec = {
            if (reduce) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (scaleIn(spring(dampingRatio = 0.55f, stiffness = 300f), initialScale = 0.6f) + fadeIn()) togetherWith
                    (scaleOut(targetScale = 0.6f) + fadeOut())
            }
        },
        label = "sendStop"
    ) { isGenerating ->
        if (isGenerating) {
            GlassIconButton(GlassIcon.Stop, "إيقاف", onStop, accent = true)
        } else {
            GlassIconButton(GlassIcon.Send, "إرسال", onSend, accent = canSend, enabled = canSend)
        }
    }
}

@Composable
private fun Bubble(m: ChatMsg) {
    val tint = when {
        m.isError -> SyronColors.Danger.copy(alpha = 0.24f)
        m.fromUser -> SyronColors.Cyan.copy(alpha = 0.34f)
        else -> Color.White.copy(alpha = 0.13f)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start
    ) {
        GlassPanel(
            modifier = Modifier.widthIn(max = 320.dp),
            cornerRadius = 22.dp,
            tint = tint,
            borderAlpha = if (m.fromUser) 0.6f else 0.42f
        ) {
            if (m.text.isEmpty()) {
                TypingDots(Modifier.padding(horizontal = 18.dp, vertical = 16.dp))
            } else {
                Text(
                    text = m.text,
                    color = if (m.isError) SyronColors.Danger else SyronColors.Text,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                )
            }
        }
    }
}

@Composable
private fun TypingDots(modifier: Modifier = Modifier) {
    val reduce = LocalGlassEnv.current.reduceMotion
    val transition = rememberInfiniteTransition(label = "typing")
    val phaseState by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "typingPhase"
    )
    Canvas(modifier.size(width = 40.dp, height = 10.dp)) {
        val phase = if (reduce) 1f else phaseState
        val r = 3.5.dp.toPx()
        for (i in 0..2) {
            val dd = abs(phase - i)
            val d = min(dd, 3f - dd)
            val a = 0.3f + 0.7f * (1f - min(d, 1f))
            drawCircle(
                color = Color.White.copy(alpha = a),
                radius = r,
                center = Offset(r + i * (size.width - 2 * r) / 2f, size.height / 2f)
            )
        }
    }
}

@Composable
private fun EmptyState(settings: SettingsViewModel, onSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "اسأل SYRON أي شيء", color = SyronColors.Text, fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = settings.personality.summary,
            color = SyronColors.TextDim,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        if (settings.loaded && !settings.hasKey && !settings.readFailed) {
            Spacer(Modifier.height(20.dp))
            GlassPanel(cornerRadius = 22.dp, tint = SyronColors.Cyan.copy(alpha = 0.1f)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "تحتاج إلى مفتاح Gemini لبدء المحادثة",
                        color = SyronColors.Text,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    GlassButton(text = "فتح الإعدادات", onClick = onSettings, accent = true, refract = false)
                }
            }
        }
    }
}
