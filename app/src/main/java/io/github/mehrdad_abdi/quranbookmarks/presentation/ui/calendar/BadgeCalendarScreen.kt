package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BadgeLevel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgeCalendarScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BadgeCalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Badge Calendar") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.goToCurrentMonth() }) {
                        Icon(
                            imageVector = Icons.Default.Today,
                            contentDescription = "Today"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading && uiState.calendarDays.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Month navigation
                item {
                    MonthNavigationBar(
                        currentMonth = uiState.currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                        onPreviousClick = { viewModel.goToPreviousMonth() },
                        onNextClick = { viewModel.goToNextMonth() }
                    )
                }

                // Calendar grid
                item {
                    CalendarGrid(
                        calendarDays = uiState.calendarDays,
                        selectedDay = uiState.selectedDay,
                        onDayClick = { day -> viewModel.selectDay(day) }
                    )
                }

                // Selected day details
                if (uiState.selectedDay != null) {
                    item {
                        SelectedDayCard(day = uiState.selectedDay!!)
                    }
                }

                // Badge legend
                item {
                    BadgeLegendCard()
                }
            }
        }
    }
}

@Composable
private fun MonthNavigationBar(
    currentMonth: String,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousClick) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Previous month"
                )
            }

            Text(
                text = currentMonth,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            IconButton(onClick = onNextClick) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Next month"
                )
            }
        }
    }
}

@Composable
private fun CalendarGrid(
    calendarDays: List<CalendarDay>,
    selectedDay: CalendarDay?,
    onDayClick: (CalendarDay) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Day of week headers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                    Text(
                        text = day,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Calendar days grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(calendarDays) { day ->
                    CalendarDayCell(
                        day = day,
                        isSelected = day.date == selectedDay?.date,
                        onClick = { onDayClick(day) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarDay,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val today = LocalDate.now()
    val isToday = day.date == today
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        day.badge != BadgeLevel.NONE -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Badge emoji or day number
            if (day.badge != BadgeLevel.NONE && day.badge.emoji.isNotEmpty()) {
                Text(
                    text = day.badge.emoji,
                    fontSize = 20.sp
                )
            } else {
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Day number
            Text(
                text = day.date.dayOfMonth.toString(),
                fontSize = 11.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    !day.isInCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun SelectedDayCard(day: CalendarDay) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = day.date.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy")),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (day.ayahCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (day.badge.emoji.isNotEmpty()) {
                        Text(
                            text = day.badge.emoji,
                            fontSize = 32.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${day.ayahCount} Ayahs",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (day.badge != BadgeLevel.NONE) {
                            Text(
                                text = day.badge.arabicName,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "No reading recorded",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BadgeLegendCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Badge Levels",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            BadgeLevel.values()
                .filter { it != BadgeLevel.NONE }
                .forEach { badge ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = badge.emoji,
                            fontSize = 20.sp,
                            modifier = Modifier.width(40.dp)
                        )
                        Text(
                            text = badge.arabicName,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${badge.threshold}+ ayahs",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
        }
    }
}
