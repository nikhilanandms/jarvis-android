package com.jarvis.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.jarvis.android.ui.AppNavigation
import com.jarvis.android.ui.theme.JarvisTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var showPermissionRationale by mutableStateOf(false)

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) showPermissionRationale = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        enableEdgeToEdge()
        setContent {
            JarvisTheme {
                AppNavigation()

                if (showPermissionRationale) {
                    AlertDialog(
                        onDismissRequest = { showPermissionRationale = false },
                        title = { Text("Microphone required") },
                        text = { Text("Jarvis needs microphone access for voice chat. Please grant it in Settings.") },
                        confirmButton = {
                            TextButton(onClick = { showPermissionRationale = false }) {
                                Text("OK")
                            }
                        }
                    )
                }
            }
        }
    }
}
