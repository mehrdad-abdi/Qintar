package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mehrdad_abdi.quranbookmarks.R
import io.github.mehrdad_abdi.quranbookmarks.domain.model.BadgeLevel
import io.github.mehrdad_abdi.quranbookmarks.presentation.ui.components.RtlIcons
import java.time.format.DateTimeFormatter
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_statistics_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = RtlIcons.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
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
                // Summary stats card
                item {
                    SummaryStatsCard(
                        totalAyahs = uiState.totalAyahs,
                        averagePerDay = uiState.averagePerDay,
                        daysRead = uiState.daysRead,
                        bestDay = uiState.bestDay
                    )
                }

                // 30-day chart
                item {
                    ChartCard(
                        dayDataList = uiState.last30Days,
                        maxValue = uiState.maxAyahsInPeriod
                    )
                }

                // Badge distribution
                item {
                    BadgeDistributionCard(
                        badgeDistribution = uiState.badgeDistribution,
                        totalDays = uiState.daysRead
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStatsCard(
    totalAyahs: Int,
    averagePerDay: Float,
    daysRead: Int,
    bestDay: DayData?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.summary_30_days),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatBox(
                    label = stringResource(R.string.total_ayahs),
                    value = totalAyahs.toString(),
                    modifier = Modifier.weight(1f)
                )
                Divider(
                    modifier = Modifier
                        .width(1.dp)
                        .height(60.dp)
                )
                StatBox(
                    label = stringResource(R.string.days_read),
                    value = daysRead.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatBox(
                    label = stringResource(R.string.average_per_day),
                    value = String.format("%.1f", averagePerDay),
                    modifier = Modifier.weight(1f)
                )
                Divider(
                    modifier = Modifier
                        .width(1.dp)
                        .height(60.dp)
                )
                StatBox(
                    label = stringResource(R.string.best_day),
                    value = bestDay?.ayahCount?.toString() ?: "—",
                    subtitle = bestDay?.date?.format(DateTimeFormatter.ofPattern("MMM dd")),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChartCard(
    dayDataList: List<DayData>,
    maxValue: Int
) {
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
                text = stringResource(R.string.last_30_days),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (dayDataList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_reading_data),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                BarChart(
                    dayDataList = dayDataList,
                    maxValue = max(maxValue, 10), // Minimum scale of 10
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }
        }
    }
}

@Composable
private fun BarChart(
    dayDataList: List<DayData>,
    maxValue: Int,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        if (dayDataList.isEmpty() || maxValue == 0) return@Canvas

        val barWidth = (canvasWidth / dayDataList.size) * 0.7f
        val spacing = (canvasWidth / dayDataList.size) * 0.3f

        dayDataList.forEachIndexed { index, dayData ->
            val barHeight = if (maxValue > 0) {
                (dayData.ayahCount.toFloat() / maxValue.toFloat()) * canvasHeight
            } else {
                0f
            }

            val x = index * (barWidth + spacing) + spacing / 2
            val y = canvasHeight - barHeight

            // Draw bar
            drawRect(
                color = if (dayData.ayahCount > 0) primaryColor else surfaceVariant,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight)
            )
        }
    }
}

@Composable
private fun BadgeDistributionCard(
    badgeDistribution: Map<BadgeLevel, Int>,
    totalDays: Int
) {
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
                text = stringResource(R.string.badge_distribution),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (badgeDistribution.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_badges_earned),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                badgeDistribution.entries
                    .sortedByDescending { it.key.threshold }
                    .forEach { (badge, count) ->
                        if (badge != BadgeLevel.NONE) {
                            BadgeDistributionRow(
                                badge = badge,
                                daysCount = count,
                                totalDays = totalDays
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
            }
        }
    }
}

@Composable
private fun BadgeDistributionRow(
    badge: BadgeLevel,
    daysCount: Int,
    totalDays: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = badge.emoji,
                    fontSize = 20.sp,
                    modifier = Modifier.width(32.dp)
                )
                Text(
                    text = stringResource(badge.nameResId),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = stringResource(R.string.days_count_format, daysCount),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Progress bar showing percentage
        val percentage = if (totalDays > 0) daysCount.toFloat() / totalDays.toFloat() else 0f
        LinearProgressIndicator(
            progress = percentage,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
