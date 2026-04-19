package com.parc.fitnesstimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.parc.fitnesstimer.ui.theme.ParcFitnessTimerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single Activity — the entire UI is Compose.
 * enableEdgeToEdge() is intentionally omitted: it conflicts with
 * adjustResize in the manifest and crashes on API 31+.
 * Compose Scaffold handles window insets automatically.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ParcFitnessTimerTheme {
                ParcNavHost()
            }
        }
    }
}
