package com.poldivers.app

import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.poldivers.app.core.AppContainer
import com.poldivers.app.core.haptics.LocalHaptics
import com.poldivers.app.ui.theme.PolDiversTheme
import kotlin.math.hypot

class MainActivity : ComponentActivity() {

    private val haptics by lazy { AppContainer.get(applicationContext).haptics }
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    /**
     * "Vibration on touch" everywhere: every tap on the screen (finger down and up without
     * scrolling) ticks, so no button or card can be forgotten. Scrolls and drags stay silent.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                downTime = ev.eventTime
            }
            MotionEvent.ACTION_UP -> {
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                val moved = hypot(ev.x - downX, ev.y - downY) > slop
                val longPress = ev.eventTime - downTime > ViewConfiguration.getLongPressTimeout()
                if (!moved && !longPress) haptics.tap()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = AppContainer.get(applicationContext)

        setContent {
            PolDiversTheme {
                CompositionLocalProvider(LocalHaptics provides container.haptics) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        PolDiversApp()
                    }
                }
            }
        }
    }
}
