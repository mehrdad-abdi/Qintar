package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.mehrdad_abdi.quranbookmarks.R
import io.github.mehrdad_abdi.quranbookmarks.data.remote.dto.ReciterData
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.components.RtlIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReciterSelectionScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReciterSelectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val navigationEvent by viewModel.navigationEvent.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Navigate back after successful selection
    LaunchedEffect(navigationEvent) {
        if (navigationEvent) {
            snackbarHostState.showSnackbar(context.getString(R.string.reciter_selected))
            kotlinx.coroutines.delay(500)
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_reciter_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(RtlIcons.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.reciters) { reciter ->
                        ReciterItem(
                            reciter = reciter,
                            isSelected = reciter.identifier == uiState.selectedReciterId,
                            isPlaying = reciter.identifier == uiState.playingReciterId,
                            isLoadingSample = reciter.identifier == uiState.loadingSampleReciterId,
                            onSelect = { viewModel.selectReciter(reciter) },
                            onPlaySample = { viewModel.playSample(reciter) }
                        )
                    }
                }
            }
        }
    }

    // Error Snackbar
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            viewModel.clearError()
        }
    }
}

@Composable
private fun ReciterItem(
    reciter: ReciterData,
    isSelected: Boolean,
    isPlaying: Boolean,
    isLoadingSample: Boolean,
    onSelect: () -> Unit,
    onPlaySample: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reciter name
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reciter.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = reciter.identifier,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Play sample button
            IconButton(
                onClick = onPlaySample,
                enabled = !isLoadingSample
            ) {
                when {
                    isLoadingSample -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    isPlaying -> Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = stringResource(R.string.pause),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    else -> Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.play_sample),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Selected indicator
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.selected),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
