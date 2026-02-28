package com.jarvis.android.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jarvis.android.ui.chat.ChatScreen
import com.jarvis.android.ui.download.DownloadScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "download") {
        composable("download") {
            DownloadScreen(
                onReady = {
                    navController.navigate("chat") {
                        popUpTo("download") { inclusive = true }
                    }
                }
            )
        }
        composable("chat") {
            ChatScreen()
        }
    }
}
