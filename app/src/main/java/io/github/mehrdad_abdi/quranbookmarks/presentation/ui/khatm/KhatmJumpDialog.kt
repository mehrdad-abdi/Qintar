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
                            val surah = selectedSurah
                            if (surah != null) {
                                // Use the surah starting page mapping
                                val startPage = getSurahStartingPage(surah.number)
                                onJumpToPage(startPage)
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

/**
 * Maps surah numbers to their starting page numbers in the Quran
 * Based on the standard Mushaf page numbering (604 pages total)
 */
private fun getSurahStartingPage(surahNumber: Int): Int {
    return when (surahNumber) {
        1 -> 1      // Al-Fatiha
        2 -> 2      // Al-Baqarah
        3 -> 50     // Aal-E-Imran
        4 -> 77     // An-Nisa
        5 -> 106    // Al-Ma'idah
        6 -> 128    // Al-An'am
        7 -> 151    // Al-A'raf
        8 -> 177    // Al-Anfal
        9 -> 187    // At-Tawbah
        10 -> 208   // Yunus
        11 -> 221   // Hud
        12 -> 235   // Yusuf
        13 -> 249   // Ar-Ra'd
        14 -> 255   // Ibrahim
        15 -> 262   // Al-Hijr
        16 -> 267   // An-Nahl
        17 -> 282   // Al-Isra
        18 -> 293   // Al-Kahf
        19 -> 305   // Maryam
        20 -> 312   // Ta-Ha
        21 -> 322   // Al-Anbiya
        22 -> 332   // Al-Hajj
        23 -> 342   // Al-Mu'minun
        24 -> 350   // An-Nur
        25 -> 359   // Al-Furqan
        26 -> 367   // Ash-Shu'ara
        27 -> 377   // An-Naml
        28 -> 385   // Al-Qasas
        29 -> 396   // Al-Ankabut
        30 -> 404   // Ar-Rum
        31 -> 411   // Luqman
        32 -> 415   // As-Sajda
        33 -> 418   // Al-Ahzab
        34 -> 428   // Saba
        35 -> 434   // Fatir
        36 -> 440   // Ya-Sin
        37 -> 446   // As-Saffat
        38 -> 453   // Sad
        39 -> 458   // Az-Zumar
        40 -> 467   // Ghafir
        41 -> 477   // Fussilat
        42 -> 483   // Ash-Shura
        43 -> 489   // Az-Zukhruf
        44 -> 496   // Ad-Dukhan
        45 -> 499   // Al-Jathiya
        46 -> 502   // Al-Ahqaf
        47 -> 507   // Muhammad
        48 -> 511   // Al-Fath
        49 -> 515   // Al-Hujurat
        50 -> 518   // Qaf
        51 -> 520   // Adh-Dhariyat
        52 -> 523   // At-Tur
        53 -> 526   // An-Najm
        54 -> 528   // Al-Qamar
        55 -> 531   // Ar-Rahman
        56 -> 534   // Al-Waqi'a
        57 -> 537   // Al-Hadid
        58 -> 542   // Al-Mujadila
        59 -> 545   // Al-Hashr
        60 -> 549   // Al-Mumtahina
        61 -> 551   // As-Saff
        62 -> 553   // Al-Jumu'a
        63 -> 554   // Al-Munafiqun
        64 -> 556   // At-Taghabun
        65 -> 558   // At-Talaq
        66 -> 560   // At-Tahrim
        67 -> 562   // Al-Mulk
        68 -> 564   // Al-Qalam
        69 -> 566   // Al-Haqqah
        70 -> 568   // Al-Ma'arij
        71 -> 570   // Nuh
        72 -> 572   // Al-Jinn
        73 -> 574   // Al-Muzzammil
        74 -> 575   // Al-Muddathir
        75 -> 577   // Al-Qiyama
        76 -> 578   // Al-Insan
        77 -> 580   // Al-Mursalat
        78 -> 582   // An-Naba
        79 -> 583   // An-Nazi'at
        80 -> 585   // Abasa
        81 -> 586   // At-Takwir
        82 -> 587   // Al-Infitar
        83 -> 587   // Al-Mutaffifin
        84 -> 589   // Al-Inshiqaq
        85 -> 590   // Al-Buruj
        86 -> 591   // At-Tariq
        87 -> 591   // Al-A'la
        88 -> 592   // Al-Ghashiya
        89 -> 593   // Al-Fajr
        90 -> 595   // Al-Balad
        91 -> 595   // Ash-Shams
        92 -> 595   // Al-Lail
        93 -> 596   // Ad-Dhuha
        94 -> 596   // Ash-Sharh
        95 -> 597   // At-Tin
        96 -> 597   // Al-Alaq
        97 -> 598   // Al-Qadr
        98 -> 598   // Al-Bayyina
        99 -> 599   // Az-Zalzala
        100 -> 599  // Al-Adiyat
        101 -> 600  // Al-Qari'a
        102 -> 600  // At-Takathur
        103 -> 601  // Al-Asr
        104 -> 601  // Al-Humaza
        105 -> 601  // Al-Fil
        106 -> 602  // Quraysh
        107 -> 602  // Al-Ma'un
        108 -> 602  // Al-Kawthar
        109 -> 603  // Al-Kafirun
        110 -> 603  // An-Nasr
        111 -> 603  // Al-Masad
        112 -> 604  // Al-Ikhlas
        113 -> 604  // Al-Falaq
        114 -> 604  // An-Nas
        else -> 1   // Default to page 1 if invalid
    }
}
