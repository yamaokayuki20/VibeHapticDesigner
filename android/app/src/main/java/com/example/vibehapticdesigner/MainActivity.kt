package com.example.vibehapticdesigner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private lateinit var hapticEngine: HapticEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hapticEngine = HapticEngine(this)

        setContent {
            MaterialTheme(
                colorScheme = androidx.compose.material3.darkColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DesignerScreen(hapticEngine)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hapticEngine.release()
    }
}
