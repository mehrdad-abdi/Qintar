package io.github.mehrdad_abdi.qintar.feature.read

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.domain.model.Quran
import io.github.mehrdad_abdi.qintar.domain.model.SurahInfo
import io.github.mehrdad_abdi.qintar.feature.khatm.KhatmSummaryCard
import io.github.mehrdad_abdi.qintar.ui.common.GoToField
import io.github.mehrdad_abdi.qintar.ui.common.ayahName
import io.github.mehrdad_abdi.qintar.ui.common.displayName
import io.github.mehrdad_abdi.qintar.ui.common.surahFacts
import io.github.mehrdad_abdi.qintar.ui.common.surahName
import io.github.mehrdad_abdi.qintar.ui.designsystem.EmptyState
import io.github.mehrdad_abdi.qintar.ui.designsystem.Hairline
import io.github.mehrdad_abdi.qintar.ui.designsystem.QCard
import io.github.mehrdad_abdi.qintar.ui.designsystem.QProgressBar
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarShapes
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarType
import io.github.mehrdad_abdi.qintar.ui.designsystem.SectionHeader
import io.github.mehrdad_abdi.qintar.ui.designsystem.Sizes
import io.github.mehrdad_abdi.qintar.ui.designsystem.Spacing
import io.github.mehrdad_abdi.qintar.ui.designsystem.formatNumber
import io.github.mehrdad_abdi.qintar.ui.designsystem.localizeDigits
import io.github.mehrdad_abdi.qintar.ui.navigation.KhatmDetailRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.KhatmListRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.NewKhatmRoute
import io.github.mehrdad_abdi.qintar.ui.navigation.ReaderRoute

/** Test tag of the Read tab's list. */
const val READ_LIST_TAG = "read-list"

private const val PAGES_PER_ROW = 6

/**
 * The Read tab (§5.3): continue browsing, go-to, random, the khatms in progress, and the surah / juz /
 * page indexes. Everything opens the reader (or a khatm screen) through [onNavigate].
 *
 * @param onNavigate pass a route object from `ui/navigation/Routes.kt`.
 */
@Composable
fun ReadScreen(onNavigate: (route: Any) -> Unit, viewModel: ReadViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val open: (ReaderRoute?) -> Unit = { route -> if (route != null) onNavigate(route) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .testTag(READ_LIST_TAG),
        contentPadding = PaddingValues(bottom = Spacing.xl),
    ) {
        item {
            Text(
                stringResource(R.string.tab_read),
                style = QintarType.Title,
                color = colors.onSurface,
                modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.lg, bottom = Spacing.sm),
            )
        }
        if (state.loading) return@LazyColumn

        state.continueBrowsing?.let { last ->
            item(key = "continue") {
                QCard(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                    onClick = { open(viewModel.continueRoute()) },
                ) {
                    Text(stringResource(R.string.read_continue_browsing), style = QintarType.Label, color = colors.onSurfaceVariant)
                    Text(
                        stringResource(
                            R.string.read_continue_browsing_at,
                            ayahName(last.surah, last.ayah),
                            formatNumber(last.page),
                        ),
                        style = QintarType.BodyStrong,
                        color = colors.onSurface,
                    )
                }
            }
        }
        item(key = "goto") { GoToCard(state, viewModel, open) }
        item(key = "khatms") { KhatmsSection(state, onNavigate) }
        item(key = "tabs") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                for ((tab, label) in listOf(
                    ReadTab.SURAH to R.string.read_tab_surah,
                    ReadTab.JUZ to R.string.read_tab_juz,
                    ReadTab.PAGE to R.string.read_tab_page,
                )) {
                    FilterChip(
                        selected = state.tab == tab,
                        onClick = { viewModel.onTab(tab) },
                        label = { Text(stringResource(label), style = QintarType.Label, modifier = Modifier.fillMaxWidth()) },
                        modifier = Modifier.weight(1f).heightIn(min = Sizes.touchTarget),
                    )
                }
            }
        }
        when (state.tab) {
            ReadTab.SURAH -> {
                item(key = "filter") {
                    OutlinedTextField(
                        value = state.filter,
                        onValueChange = viewModel::onFilter,
                        label = { Text(stringResource(R.string.read_filter_label)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        singleLine = true,
                        shape = QintarShapes.Inner,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
                    )
                }
                val rows = state.visibleSurahs
                if (rows.isEmpty()) {
                    item(key = "no-match") {
                        Text(
                            stringResource(R.string.read_no_match),
                            style = QintarType.Body,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.screen),
                        )
                    }
                }
                items(rows, key = { "surah-${it.surah.number}" }) { row ->
                    SurahRow(row, onClick = { open(viewModel.surahRoute(row.surah.number)) })
                }
            }
            ReadTab.JUZ -> items(state.juz, key = { "juz-${it.juz}" }) { row ->
                JuzRow(row, onClick = { open(viewModel.juzRoute(row.juz)) })
            }
            ReadTab.PAGE -> items((1..Quran.PAGE_COUNT step PAGES_PER_ROW).toList(), key = { "pages-$it" }) { first ->
                PageGridRow(first, onPage = { open(viewModel.pageRoute(it)) })
            }
        }
    }
}

@Composable
private fun GoToCard(state: ReadUiState, viewModel: ReadViewModel, open: (ReaderRoute?) -> Unit) {
    QCard(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
        GoToField(
            value = state.goToInput,
            onValueChange = viewModel::onGoToInput,
            onSubmit = { open(viewModel.submitGoTo()) },
            problem = state.goToProblem,
        )
        Spacer(Modifier.height(Spacing.sm))
        SurahChooser(state, viewModel)
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(
                onClick = { open(viewModel.randomAyah()) },
                modifier = Modifier.weight(1f).heightIn(min = Sizes.touchTarget),
            ) {
                Icon(Icons.Outlined.Casino, null, modifier = Modifier.size(Sizes.iconSmall))
                Spacer(Modifier.size(Spacing.sm))
                Text(stringResource(R.string.read_random_ayah), style = QintarType.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = { open(viewModel.randomPage()) },
                modifier = Modifier.weight(1f).heightIn(min = Sizes.touchTarget),
            ) {
                Icon(Icons.Outlined.Casino, null, modifier = Modifier.size(Sizes.iconSmall))
                Spacer(Modifier.size(Spacing.sm))
                Text(stringResource(R.string.read_random_page), style = QintarType.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "Bare ayah numbers refer to: [Surah ▾]": the surah that `255` is read in. */
@Composable
private fun SurahChooser(state: ReadUiState, viewModel: ReadViewModel) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.read_goto_surah_label),
            style = QintarType.Label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box {
            TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                Text(state.goToSurah?.let { surahName(it) } ?: stringResource(R.string.read_goto_surah_none), style = QintarType.Label)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.read_goto_surah_none)) },
                    onClick = { open = false; viewModel.onGoToSurah(null) },
                )
                for (row in state.surahs) {
                    DropdownMenuItem(
                        text = { Text(localizeDigits("${row.surah.number}. ") + surahName(row.surah)) },
                        onClick = { open = false; viewModel.onGoToSurah(row.surah.number) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KhatmsSection(state: ReadUiState, onNavigate: (Any) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
        SectionHeader(
            stringResource(R.string.read_khatms_header),
            actionLabel = stringResource(R.string.read_khatms_all),
            onAction = { onNavigate(KhatmListRoute) },
        )
        Spacer(Modifier.height(Spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for (khatm in state.khatms) {
                KhatmSummaryCard(
                    item = khatm,
                    compact = true,
                    onOpen = { onNavigate(KhatmDetailRoute(khatm.id)) },
                    onContinue = { onNavigate(ReaderRoute.khatm(khatm.id)) },
                )
            }
            OutlinedButton(
                onClick = { onNavigate(NewKhatmRoute) },
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget),
            ) { Text(stringResource(R.string.read_new_khatm), style = QintarType.Label) }
        }
    }
}

@Composable
private fun SurahRow(row: SurahRowState, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val language = LocalConfiguration.current.locales[0].language
    val arabicFirst = language == "fa" || language == "ar"
    val surah = row.surah
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(colors.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) { Text(formatNumber(surah.number), style = QintarType.Label, color = colors.onSurface) }
            Column(Modifier.weight(1f)) {
                Text(surah.displayName(language), style = QintarType.BodyStrong, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(surahFacts(surah), style = QintarType.Caption, color = colors.onSurfaceVariant, maxLines = 1)
            }
            Text(
                if (arabicFirst) surah.nameLatin else surah.nameAr,
                style = QintarType.Label,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Only with a khatm in progress, and in place of the divider so there is never a double line.
        val coverage = row.coverage
        if (coverage != null) {
            val coverageText = stringResource(
                R.string.surah_coverage_description,
                stringResource(R.string.percent, formatNumber((coverage * 100).toInt())),
            )
            QProgressBar(
                progress = coverage,
                height = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.xs)
                    .clearAndSetSemantics { contentDescription = coverageText },
            )
            return@Column
        }
        Hairline(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs))
    }
}

@Composable
private fun JuzRow(row: JuzRowState, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.juz_n, formatNumber(row.juz)), style = QintarType.BodyStrong, color = colors.onSurface, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.juz_starts_at, ayahName(row.startSurah, row.start.ayah)),
                style = QintarType.Label,
                color = colors.onSurfaceVariant,
            )
        }
        Hairline(Modifier.padding(horizontal = Spacing.screen))
    }
}

@Composable
private fun PageGridRow(first: Int, onPage: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.xxs), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        for (page in first until first + PAGES_PER_ROW) {
            if (page > Quran.PAGE_COUNT) {
                Spacer(Modifier.weight(1f))
                continue
            }
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = Sizes.touchTarget)
                    .clip(QintarShapes.Inner)
                    .background(colors.surfaceContainer)
                    .clickable(role = Role.Button) { onPage(page) },
                contentAlignment = Alignment.Center,
            ) { Text(formatNumber(page), style = QintarType.Numeric, color = colors.onSurface) }
        }
    }
}
