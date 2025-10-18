package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.khatm

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.mehrdad_abdi.quranbookmarks.R
import io.github.mehrdad_abdi.quranbookmarks.domain.model.JuzPageMapping
import io.github.mehrdad_abdi.quranbookmarks.domain.model.KhatmProgress
import io.github.mehrdad_abdi.quranbookmarks.domain.model.Surah
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.components.SurahDropdown

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun KhatmJumpDialog(
    currentPage: Int,
    surahs: List<Surah>,
    onJumpToPage: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var pageInput by remember { mutableStateOf(currentPage.toString()) }
    var selectedJuz by remember { mutableIntStateOf(1) }
    var selectedSurah by remember { mutableStateOf<Surah?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.jump_to))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = {
                            selectedTabIndex = 0
                            errorMessage = null
                        },
                        text = { Text(stringResource(R.string.tab_page)) }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = {
                            selectedTabIndex = 1
                            errorMessage = null
                        },
                        text = { Text(stringResource(R.string.tab_juz)) }
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = {
                            selectedTabIndex = 2
                            errorMessage = null
                        },
                        text = { Text(stringResource(R.string.tab_surah)) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tab Content
                when (selectedTabIndex) {
                    0 -> {
                        // Page Tab
                        OutlinedTextField(
                            value = pageInput,
                            onValueChange = {
                                pageInput = it
                                errorMessage = null
                            },
                            label = { Text(stringResource(R.string.enter_page_number)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = errorMessage != null
                        )
                    }
                    1 -> {
                        // Juz Tab
                        Text(
                            text = stringResource(R.string.select_juz),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Juz Dropdown
                        var expanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = stringResource(R.string.juz_format, selectedJuz),
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                JuzPageMapping.getAllJuz().forEach { juz ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.juz_format, juz)) },
                                        onClick = {
                                            selectedJuz = juz
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    2 -> {
                        // Surah Tab
                        SurahDropdown(
                            selectedSurah = selectedSurah,
                            surahs = surahs,
                            onSurahSelected = { surah ->
                                selectedSurah = surah
                                errorMessage = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Error message
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (selectedTabIndex) {
                        0 -> {
                            // Page jump
                            val page = pageInput.toIntOrNull()
                            if (page != null && page in KhatmProgress.MIN_PAGE..KhatmProgress.MAX_PAGE) {
                                onJumpToPage(page)
                            } else {
                                errorMessage = context.getString(R.string.invalid_page)
                            }
                        }
                        1 -> {
                            // Juz jump
                            val startPage = JuzPageMapping.getStartingPage(selectedJuz)
                            if (startPage != null) {
                                onJumpToPage(startPage)
                            }
                        }
                        2 -> {
                            // Surah jump
                            if (selectedSurah != null) {
                                // Use the first ayah of the surah to get page number
                                // We'll need to load the page for surah 1, ayah 1
                                // For now, use surah number as approximation
                                // This should ideally fetch the actual page from API
                                onJumpToPage(1) // TODO: Get actual starting page of surah
                            } else {
                                errorMessage = context.getString(R.string.error_select_surah)
                            }
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.jump_to))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = modifier
    )
}
