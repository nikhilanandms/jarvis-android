package com.jarvis.android.ui.download

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jarvis.android.ui.theme.Gold
import com.jarvis.android.ui.theme.GoldDim
import com.jarvis.android.ui.theme.ObsidianBg
import com.jarvis.android.ui.theme.ObsidianBorder
import com.jarvis.android.ui.theme.ObsidianVariant
import com.jarvis.android.ui.theme.Positive
import com.jarvis.android.ui.theme.TextPrimary
import com.jarvis.android.ui.theme.TextSecondary

@Composable
fun DownloadScreen(
    onReady: () -> Unit,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val models by viewModel.models.collectAsState()

    LaunchedEffect(models) {
        if (viewModel.allReady) onReady()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top — wordmark
        Column(modifier = Modifier.padding(top = 64.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Gold, CircleShape)
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    "JARVIS",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Light,
                        letterSpacing = 8.sp
                    ),
                    color = TextPrimary
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Model setup required",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        // Middle — model status cards
        Column(
            modifier = Modifier.padding(vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            models.forEach { model ->
                ModelCard(model)
            }

            if (models.isNotEmpty() && !viewModel.allReady) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianVariant)
                        .border(0.5.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            "ADB COMMAND",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "adb push <model> \\\n  /sdcard/Android/data/com.jarvis.android/files/",
                            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 18.sp),
                            color = Gold.copy(alpha = 0.8f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Bottom — check button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            TextButton(onClick = { viewModel.checkModels() }) {
                Text(
                    "CHECK AGAIN",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                    color = Gold
                )
            }
        }
    }
}

@Composable
private fun ModelCard(model: ModelStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianVariant)
            .border(
                width = 0.5.dp,
                color = if (model.exists) Gold.copy(alpha = 0.3f) else ObsidianBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                model.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                model.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                model.path.substringAfterLast("/"),   // just the filename
                style = MaterialTheme.typography.labelSmall,
                color = if (model.exists) Gold.copy(alpha = 0.6f) else TextSecondary,
                fontFamily = FontFamily.Monospace
            )
        }

        // Status dot
        Box(
            modifier = Modifier
                .padding(start = 16.dp)
                .size(8.dp)
                .background(
                    color = if (model.exists) Positive else ObsidianBorder,
                    shape = CircleShape
                )
        )
    }
}
