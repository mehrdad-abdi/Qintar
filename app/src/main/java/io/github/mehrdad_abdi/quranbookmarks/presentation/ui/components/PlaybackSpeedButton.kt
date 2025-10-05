package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mehrdad_abdi.quranbookmarks.domain.service.PlaybackSpeed

@Composable
fun PlaybackSpeedButton(
    currentSpeed: PlaybackSpeed,
    onSpeedChange: (PlaybackSpeed) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        IconButton(
            onClick = {
                val nextSpeed = when (currentSpeed) {
                    PlaybackSpeed.SPEED_0_5 -> PlaybackSpeed.SPEED_0_75
                    PlaybackSpeed.SPEED_0_75 -> PlaybackSpeed.SPEED_1
                    PlaybackSpeed.SPEED_1 -> PlaybackSpeed.SPEED_1_25
                    PlaybackSpeed.SPEED_1_25 -> PlaybackSpeed.SPEED_1_5
                    PlaybackSpeed.SPEED_1_5 -> PlaybackSpeed.SPEED_2
                    PlaybackSpeed.SPEED_2 -> PlaybackSpeed.SPEED_0_5
                }
                onSpeedChange(nextSpeed)
            }
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = "Playback Speed: ${currentSpeed.displayText}"
            )
        }
        // Speed badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-4).dp, y = (-4).dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 3.dp, vertical = 1.dp)
        ) {
            Text(
                text = currentSpeed.displayText,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = Color.White
            )
        }
    }
}
