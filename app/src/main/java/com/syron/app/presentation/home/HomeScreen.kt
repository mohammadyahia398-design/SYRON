package com.syron.app.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syron.app.core.glass.GlassButton
import com.syron.app.core.glass.GlassIcon
import com.syron.app.core.glass.GlassIconButton
import com.syron.app.core.glass.GlassSurface
import com.syron.app.core.glass.plainClick
import com.syron.app.core.theme.SyronColors
import com.syron.app.presentation.chat.ChatViewModel
import com.syron.app.presentation.orb.SyronOrb

@Composable
fun HomeScreen(vm: ChatViewModel, onChat: () -> Unit, onSettings: () -> Unit) {
    val status = if (vm.hasKey) "جاهز · ${vm.model}" else "أضف مفتاح Gemini من الإعدادات"

    Box(Modifier.fillMaxSize()) {
        SyronOrb(
            modifier = Modifier.fillMaxSize(),
            orbSize = 300.dp,
            interactive = true,
            busy = vm.generating
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlassSurface(Modifier.plainClick(onSettings), cornerRadius = 22.dp) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "الشخصية: ${vm.personality.name}",
                        color = SyronColors.Text,
                        fontSize = 13.sp
                    )
                    Text(
                        text = status,
                        color = if (vm.hasKey) SyronColors.TextDim else SyronColors.Amber,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SYRON",
                color = SyronColors.Text,
                fontSize = 13.sp,
                letterSpacing = 6.sp
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassButton(text = "ابدأ المحادثة", onClick = onChat, accent = true)
                Spacer(Modifier.width(12.dp))
                GlassIconButton(GlassIcon.Sliders, "الإعدادات", onSettings)
            }
        }
    }
}
