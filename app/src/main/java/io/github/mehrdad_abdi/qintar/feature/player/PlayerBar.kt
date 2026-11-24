package io.github.mehrdad_abdi.qintar.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.playback.PlaybackProblem
import io.github.mehrdad_abdi.qintar.playback.Reciters
import io.github.mehrdad_abdi.qintar.ui.common.surahName
import io.github.mehrdad_abdi.qintar.ui.designsystem.MiniPlayer
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarShapes
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.designsystem.localizeDigits
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute

/** Test tags of the two forms of the mini player. */
const val PLAYER_BAR_TAG = "player-bar"
const val READER_PLAYER_TAG = "reader-player"

/** "1×", "1.25×". */
@Composable
fun speedLabel(speed: Float): String {
    val text = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')
    return localizeDigits("$text×")
}

/** "Al-Anfal 2 · Alafasy", or what went wrong. */
@Composable
private fun playerTitle(state: PlayerBarState): String {
    when (state.problem) {
        PlaybackProblem.OFFLINE -> return stringResource(R.string.audio_needs_connection)
        PlaybackProblem.FAILED -> return stringResource(R.string.audio_failed)
        null -> Unit
    }
    val surah = state.surah?.let { surahName(it) }.orEmpty()
    val what = if (state.dhikr) {
        stringResource(R.string.audio_title_sajda_dhikr)
    } else {
        // The Bismillah is titled as the surah's first ayah (owner, 2026-11-25).
        stringResource(R.string.audio_title_ayah, surah, formatNumber(state.ayahNumber))
    }
    val reciter = state.reciterId?.let { stringResource(Reciters.of(it).name) } ?: return what
    return stringResource(R.string.audio_title_with_reciter, what, reciter)
}

/**
 * The mini player above the tabs: shown only while something is loaded in the player. A tap on it
 * goes back to the reader the audio came from, at the ayah being played.
 */
@Composable
fun PlayerBar(viewModel: PlayerBarViewModel, onOpen: (ReaderRoute) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.visible) return
    MiniPlayer(
        title = playerTitle(state),
        isPlaying = state.playing || state.loading,
        speedLabel = speedLabel(state.speed),
        onPlayPause = viewModel::onPlayPause,
        onSpeed = viewModel::onSpeed,
        onClose = viewModel::onClose,
        modifier = modifier.testTag(PLAYER_BAR_TAG),
        onClick = state.returnRoute?.let { route -> { onOpen(route) } },
    )
}

/**
 * The player in the reader: the same, with previous and next ayah and the repeat menu.
 *
 * @param onOpen the title was tapped: show the ayah being played (in this reader or in the one the
 *   audio came from).
 */
@Composable
fun ReaderPlayerBar(viewModel: PlayerBarViewModel, onOpen: (ReaderRoute) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.visible) return
    ReaderPlayerBar(
        state = state,
        onPlayPause = viewModel::onPlayPause,
        onPrevious = viewModel::onPrevious,
        onNext = viewModel::onNext,
        onSpeed = viewModel::onSpeed,
        onRepeatAyah = viewModel::onRepeatAyah,
        onRepeatPassage = viewModel::onRepeatPassage,
        onClose = viewModel::onClose,
        onOpen = { state.returnRoute?.let(onOpen) },
        modifier = modifier,
    )
}

@Composable
fun ReaderPlayerBar(
    state: PlayerBarState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSpeed: () -> Unit,
    onRepeatAyah: (Int) -> Unit,
    onRepeatPassage: (Boolean) -> Unit,
    onClose: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().testTag(READER_PLAYER_TAG),
        shape = QintarShapes.Card,
        color = colors.surfaceContainer,
        contentColor = colors.onSurface,
        border = BorderStroke(Sizes.hairline, colors.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val show = stringResource(R.string.player_show_ayah)
                Text(
                    playerTitle(state),
                    style = QintarType.Label,
                    color = if (state.problem != null) colors.error else colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Sizes.touchTarget)
                        .clickable(onClickLabel = show, role = Role.Button, onClick = onOpen)
                        .padding(horizontal = Spacing.md, vertical = Spacing.md),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(Sizes.touchTarget)) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.player_close), tint = colors.onSurfaceVariant)
                }
            }
            // Transport controls keep their order in right-to-left languages, as on every player.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(Sizes.touchTarget)) {
                        Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.player_previous), tint = colors.onSurface)
                    }
                    IconButton(onClick = onPlayPause, modifier = Modifier.size(Sizes.touchTarget)) {
                        Box(Modifier.size(36.dp).clip(QintarShapes.Pill).background(colors.primary), contentAlignment = Alignment.Center) {
                            val active = state.playing || state.loading
                            Icon(
                                if (active) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                stringResource(if (active) R.string.player_pause else R.string.player_play),
                                tint = colors.onPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(Sizes.touchTarget)) {
                        Icon(Icons.Rounded.SkipNext, stringResource(R.string.player_next), tint = colors.onSurface)
                    }
                    if (state.loading) CircularProgressIndicator(Modifier.padding(start = Spacing.sm).size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.weight(1f))
                    val label = speedLabel(state.speed)
                    val speedDescription = stringResource(R.string.player_speed, label)
                    TextButton(
                        onClick = onSpeed,
                        modifier = Modifier.heightIn(min = Sizes.touchTarget).semantics { contentDescription = speedDescription },
                    ) { Text(label, style = QintarType.Numeric) }
                    RepeatMenu(state, onRepeatAyah, onRepeatPassage)
                }
            }
        }
    }
}

@Composable
private fun RepeatMenu(state: PlayerBarState, onRepeatAyah: (Int) -> Unit, onRepeatPassage: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val repeating = state.repeat.ayahTimes > 1 || state.repeat.passage
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(Sizes.touchTarget)) {
            Icon(Icons.Rounded.Repeat, stringResource(R.string.player_repeat), tint = if (repeating) colors.primary else colors.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (times in PlayerBarViewModel.AYAH_REPEATS) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (times == 1) {
                                stringResource(R.string.player_repeat_ayah_once)
                            } else {
                                stringResource(R.string.player_repeat_ayah_times, formatNumber(times))
                            },
                        )
                    },
                    trailingIcon = { if (state.repeat.ayahTimes == times) Icon(Icons.Rounded.Check, null) },
                    onClick = {
                        open = false
                        onRepeatAyah(times)
                    },
                )
            }
            HorizontalDivider(color = colors.outlineVariant)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_repeat_passage)) },
                trailingIcon = { if (state.repeat.passage) Icon(Icons.Rounded.Check, null) },
                onClick = {
                    open = false
                    onRepeatPassage(!state.repeat.passage)
                },
            )
        }
    }
}
