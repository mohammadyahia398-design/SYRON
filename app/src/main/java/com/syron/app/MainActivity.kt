package com.syron.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.syron.app.presentation.SyronRoot
import com.syron.app.presentation.chat.ChatViewModel
import com.syron.app.presentation.chat.ChatViewModelFactory

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels {
        ChatViewModelFactory((application as SyronApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent { SyronRoot(vm) }
    }
}
