package com.poldivers.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.poldivers.app.core.AppContainer
import com.poldivers.app.core.haptics.LocalHaptics
import com.poldivers.app.ui.theme.PolDiversTheme
import com.poldivers.app.ui.theme.hudBackground

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = AppContainer.get(applicationContext)

        setContent {
            PolDiversTheme {
                val alwaysTranslate by container.preferences.alwaysTranslate.collectAsState()
                val animations by container.preferences.animations.collectAsState()
                CompositionLocalProvider(
                    LocalHaptics provides container.haptics,
                    com.poldivers.app.ui.common.LocalAutoTranslate provides alwaysTranslate,
                    com.poldivers.app.ui.anim.LocalAnimations provides animations,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize().hudBackground(),
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        // A transparent surface has no default content colour -> text would be black.
                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                    ) {
                        PolDiversApp()
                    }
                }
            }
        }
    }
}
