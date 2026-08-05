package com.callbloqued.sift.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.callbloqued.sift.presentation.navigation.SiftNavHost
import com.callbloqued.sift.presentation.theme.SiftTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity entry point for Sift.
 *
 * Annotated with [AndroidEntryPoint] so that Hilt can inject dependencies into this activity
 * and into any composables that request a [dagger.hilt.android.lifecycle.HiltViewModel].
 *
 * Hosts [SiftNavHost] which owns the [androidx.navigation.NavHostController] and the
 * bottom-navigation scaffold. All navigation and screen composition happens inside that
 * composable tree; this activity remains a thin host with no business logic.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Initialises the activity, applies edge-to-edge display, and sets the Compose content.
     *
     * @param savedInstanceState Bundle containing any previously saved instance state, or null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SiftTheme {
                SiftNavHost()
            }
        }
    }
}
