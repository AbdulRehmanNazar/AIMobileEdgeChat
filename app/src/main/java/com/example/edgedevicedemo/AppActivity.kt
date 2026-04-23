package com.example.edgedevicedemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.example.edgedevicedemo.ui.EdgeChatApp
import com.example.edgedevicedemo.ui.theme.EdgeDeviceDemoTheme

class AppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
                EdgeChatApp()
        }
    }



}