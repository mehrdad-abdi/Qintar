package io.github.mehrdad_abdi.qintar.feature.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.ui.common.ScreenTopBar
import io.github.mehrdad_abdi.qintar.ui.common.bookmarkTitleText
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarShapes
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.navigation.ArrangeRoute
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

const val ARRANGE_LIST_TAG = "arrange-list"

/**
 * Arrange (owner, 2026-11-25): the Bookmarks list as titles only, one line each, moved by dragging
 * the handle. A group opens its own Arrange for its bookmarks. TalkBack users get Move up / Move down.
 */
@Composable
fun ArrangeScreen(
    route: ArrangeRoute,
    onBack: () -> Unit,
    onNavigate: (route: Any) -> Unit,
    viewModel: ArrangeViewModel = hiltViewModel<ArrangeViewModel, ArrangeViewModel.Factory> { it.create(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val reorderable = rememberReorderableLazyListState(listState) { from, to -> viewModel.onMove(from.index, to.index) }
    val moveUp = stringResource(R.string.grp_move_up)
    val moveDown = stringResource(R.string.grp_move_down)

    Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenTopBar(state.groupName ?: stringResource(R.string.arrange_title), onBack)
        Text(
            stringResource(if (route.groupId == ArrangeRoute.TOP) R.string.arrange_hint else R.string.arrange_hint_group),
            style = QintarType.Caption,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag(ARRANGE_LIST_TAG),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl),
        ) {
            itemsIndexed(state.lines, key = { _, line -> line.key.toString() }) { index, line ->
                ReorderableItem(reorderable, key = line.key.toString()) { dragging ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xxs)
                            .clip(QintarShapes.Inner)
                            .background(if (dragging) colors.surfaceContainerHigh else colors.surface)
                            .then(
                                if (line.isGroup) {
                                    Modifier.clickable(role = Role.Button) { onNavigate(ArrangeRoute(line.key.id)) }
                                } else {
                                    Modifier
                                },
                            )
                            .heightIn(min = Sizes.touchTarget)
                            .semantics {
                                customActions = listOfNotNull(
                                    CustomAccessibilityAction(moveUp) { viewModel.onMove(index, index - 1); viewModel.onMoveDone(); true }
                                        .takeIf { index > 0 },
                                    CustomAccessibilityAction(moveDown) { viewModel.onMove(index, index + 1); viewModel.onMoveDone(); true }
                                        .takeIf { index < state.lines.lastIndex },
                                )
                            }
                            .padding(start = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                line.groupName ?: line.title?.let { bookmarkTitleText(it) }.orEmpty(),
                                style = if (line.isGroup) QintarType.BodyStrong else QintarType.Body,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (line.isGroup) {
                                Text(
                                    pluralStringResource(R.plurals.arrange_group_count, line.count, formatNumber(line.count)),
                                    style = QintarType.Caption,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                        if (line.isGroup) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.onSurfaceVariant)
                        IconButton(
                            onClick = {},
                            modifier = Modifier.size(Sizes.touchTarget).draggableHandle(onDragStopped = { viewModel.onMoveDone() }),
                        ) {
                            Icon(Icons.Rounded.DragHandle, stringResource(R.string.arrange_move), tint = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
