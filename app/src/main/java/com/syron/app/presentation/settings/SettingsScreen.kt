package com.syron.app.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syron.app.core.glass.GlassButton
import com.syron.app.core.glass.GlassField
import com.syron.app.core.glass.GlassIcon
import com.syron.app.core.glass.GlassIconButton
import com.syron.app.core.glass.GlassPanel
import com.syron.app.core.glass.drawGlassIcon
import com.syron.app.core.glass.plainClick
import com.syron.app.core.theme.SyronColors
import com.syron.app.domain.model.Personalities
import com.syron.app.domain.model.Personality
import com.syron.app.presentation.chat.ChatViewModel
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    var keyInput by remember { mutableStateOf("") }
    var modelInput by remember { mutableStateOf(vm.model) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (saved) {
            delay(1800L)
            saved = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(GlassIcon.Back, "رجوع", onBack, diameter = 44.dp)
            Spacer(Modifier.width(12.dp))
            Text(text = "الإعدادات", color = SyronColors.Text, fontSize = 22.sp)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionCard("مفتاح Gemini") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(if (vm.hasKey) SyronColors.Good else SyronColors.TextDim, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (vm.hasKey) "محفوظ ومشفّر على الجهاز" else "لم يُضف بعد",
                        color = SyronColors.TextDim,
                        fontSize = 13.sp
                    )
                }
                FieldBox {
                    GlassField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        placeholder = "الصق مفتاح Gemini هنا",
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        singleLine = true,
                        password = true,
                        ltr = true
                    )
                }
                if (vm.hasKey) {
                    GlassButton(text = "حذف المفتاح", onClick = { vm.removeKey() }, refract = false)
                }
            }

            SectionCard("النموذج") {
                FieldBox {
                    GlassField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        placeholder = ChatViewModel.DEFAULT_MODEL,
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        singleLine = true,
                        ltr = true
                    )
                }
                Text(
                    text = "إن ظهرت رسالة «النموذج غير موجود» فغيّر الاسم هنا. الافتراضي: ${ChatViewModel.DEFAULT_MODEL}",
                    color = SyronColors.TextDim,
                    fontSize = 12.sp
                )
            }

            SectionCard("الشخصية") {
                Personalities.all.forEach { p ->
                    PersonalityRow(p, p.id == vm.personalityId) { vm.selectPersonality(p.id) }
                }
            }
        }

        AnimatedVisibility(visible = saved) {
            Text(
                text = "تم الحفظ ✓",
                color = SyronColors.Good,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            GlassButton(
                text = "حفظ",
                onClick = {
                    vm.saveSettings(keyInput, modelInput)
                    keyInput = ""
                    saved = true
                },
                modifier = Modifier.weight(1f),
                accent = true
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, color = SyronColors.Cyan, fontSize = 14.sp)
            content()
        }
    }
}

@Composable
private fun FieldBox(content: @Composable () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 16.dp, tint = Color.Black.copy(alpha = 0.25f)) {
        content()
    }
}

@Composable
private fun PersonalityRow(p: Personality, selected: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(
        targetValue = if (selected) SyronColors.Cyan.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
        label = "personaTint"
    )
    GlassPanel(
        modifier = Modifier.fillMaxWidth().plainClick(onClick),
        cornerRadius = 18.dp,
        tint = tint,
        borderAlpha = if (selected) 0.6f else 0.25f
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = p.name, color = SyronColors.Text, fontSize = 16.sp)
                Text(text = p.summary, color = SyronColors.TextDim, fontSize = 13.sp)
            }
            if (selected) {
                Canvas(Modifier.size(22.dp)) { drawGlassIcon(GlassIcon.Check, SyronColors.Cyan, false) }
            }
        }
    }
}
