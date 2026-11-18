package com.avalemon.spotcheck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.avalemon.spotcheck.ui.nav.AppNavHost
import com.avalemon.spotcheck.ui.theme.SpotCheckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpotCheckTheme {
                AppNavHost()
            }
        }
    }
}
