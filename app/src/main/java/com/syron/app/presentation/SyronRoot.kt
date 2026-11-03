package com.syron.app.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import com.syron.app.core.glass.GlassEnv
import com.syron.app.core.glass.LocalGlassEnv
import com.syron.app.core.glass.drawScene
import com.syron.app.core.glass.rememberReduceMotion
import com.syron.app.core.theme.SyronColors
import com.syron.app.presentation.chat.ChatScreen
import com.syron.app.presentation.chat.ChatViewModel
import com.syron.app.presentation.home.HomeScreen
import com.syron.app.presentation.settings.SettingsScreen

enum class Screen { Home, Chat, Settings }

@Composable
fun SyronRoot(vm: ChatViewModel) {
    val reduce = rememberReduceMotion()
    val env = remember(reduce) { GlassEnv(reduce) }
    val stack = remember { mutableStateListOf(Screen.Home) }
    val current = stack.last()

    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    val go: (Screen) -> Unit = { s -> if (stack.last() != s) stack.add(s) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    CompositionLocalProvider(LocalGlassEnv provides env) {
        Box(
            Modifier
                .fillMaxSize()
                .background(SyronColors.Bg)
                .onSizeChanged { env.rootSize = it }
        ) {
            Canvas(Modifier.fillMaxSize()) { drawScene(size.width, size.height) }

            AnimatedContent(
                targetState = current,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (reduce) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.96f)) togetherWith
                            fadeOut(tween(160))
                    }
                },
                label = "screens"
            ) { s ->
                when (s) {
                    Screen.Home -> HomeScreen(
                        vm = vm,
                        onChat = { go(Screen.Chat) },
                        onSettings = { go(Screen.Settings) }
                    )
                    Screen.Chat -> ChatScreen(
                        vm = vm,
                        onBack = back,
                        onSettings = { go(Screen.Settings) }
                    )
                    Screen.Settings -> SettingsScreen(vm = vm, onBack = back)
                }
            }
        }
    }
}
