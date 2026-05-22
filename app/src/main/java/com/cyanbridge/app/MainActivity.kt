package com.cyanbridge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.cyanbridge.app.ui.navigation.CyanBridgeNavGraph
import com.cyanbridge.app.ui.theme.CyanBridgeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CyanBridgeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CyanBridgeNavGraph()
                }
            }
        }
    }
}
