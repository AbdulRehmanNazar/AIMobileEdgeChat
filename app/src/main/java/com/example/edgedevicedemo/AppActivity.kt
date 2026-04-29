package com.example.edgedevicedemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle

class AppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val darkSystemBarColor = getColor(R.color.status_bar_dark_gray)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(darkSystemBarColor),
            navigationBarStyle = SystemBarStyle.dark(darkSystemBarColor)
        )

        setContent {
            AndroidEdgeChatApp()
        }
    }
}
