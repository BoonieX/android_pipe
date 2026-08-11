package com.booniex.pipes

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.booniex.pipes.ui.PipeCounterApp
import com.booniex.pipes.ui.ScanViewModel
import com.booniex.pipes.ui.theme.PipesTheme

class MainActivity : ComponentActivity() {
    private val vm: ScanViewModel by viewModels()
    private var volumeCaptureHandler: (() -> Boolean)? = null
    private var volumeUpConsumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PipesTheme {
                Surface(Modifier.fillMaxSize()) {
                    PipeCounterApp(
                        vm = vm,
                        onRegisterVolumeCapture = { handler -> volumeCaptureHandler = handler },
                    )
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) {
                    volumeUpConsumed = volumeCaptureHandler?.invoke() == true
                }
                if (volumeUpConsumed) return true
            } else if (event.action == KeyEvent.ACTION_UP && volumeUpConsumed) {
                volumeUpConsumed = false
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
