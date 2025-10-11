package io.github.mehrdad_abdi.quranbookmarks.presentation.ui.hadith

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HadithScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Hadith about Quran Reading",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Arabic Text
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Arabic",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Divider()
                    Text(
                        text = """قال رسول الله (ص):
من قَرَأَ عَشْرَ آیاتٍ فی لَیْلَةٍ لَمْ یُکْتَبْ مِنَ الْغافِلینَ،
وَمَنْ‌قَرَأَ خَمْسینَ آیَةً کُتِبَ مِنَ الذّاکِرینَ،‌
وَمَنْ قَرَأَ‌ مِائَة آیةٍ کتب من القانِتینَ،
وَمَنْ‌ قَرَأَ مِائتی آیَةٍ کُتِبَ مِنَ الْخاشِعینَ،
وَمَنْ قَرَأَ ثَلاثَ مِئَةِ آیةٍ کُتِبَ مِنَ الْفائِزینَ،
وَمَنْ قَرَأَ خَمْسَ مِائَةِ آیةٍ کُتِبَ مِنَ المُجْتَهدینَ،
وَمَنْ قَرَأَ ألْفَ آیةٍ کُتِبَ لَهُ قِنْطارٌ مِنْ بِرًْ القِنطارُ خَمْسَهَ عَشَرَ ألفَ (خَمْسونَ الفَ) مِثْقالٍ أرْبَعَةٌ وَعِشْرونَ قیراطاً أصْغَرُها مِثْلُ جَبَلِ اُحُدٍ وَأَکْبَرُها مابَیْنَ السماءِ وَالارض""",
                        fontSize = 20.sp,
                        lineHeight = 36.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // English Translation
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "English Translation",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Divider()
                    Text(
                        text = """The Messenger of Allah (peace be upon him) said:

"Whoever recites ten verses in a night will not be written among the negligent,

And whoever recites fifty verses will be written among those who remember Allah,

And whoever recites one hundred verses will be written among the devout,

And whoever recites two hundred verses will be written among the humble,

And whoever recites three hundred verses will be written among the successful,

And whoever recites five hundred verses will be written among the diligent,

And whoever recites one thousand verses will be written for him a qintar of righteousness. The qintar is fifteen thousand (fifty thousand) mithqals, twenty-four carats, the smallest of which is like Mount Uhud and the largest is what is between heaven and earth.""",
                        fontSize = 16.sp,
                        lineHeight = 28.sp,
                        textAlign = TextAlign.Left,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Persian Translation
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Persian Translation",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Divider()
                    Text(
                        text = """رسول خدا (صلی‌الله‌علیه‌و‌آله) فرمود:

«هر کس ده آیه در شب بخواند، از غافلان نوشته نخواهد شد،

و هر کس پنجاه آیه بخواند، از یادکنندگان (خدا) نوشته خواهد شد،

و هر کس صد آیه بخواند، از فرمانبرداران نوشته خواهد شد،

و هر کس دویست آیه بخواند، از خاشعان نوشته خواهد شد،

و هر کس سیصد آیه بخواهد، از رستگاران نوشته خواهد شد،

و هر کس پانصد آیه بخواند، از کوشندگان نوشته خواهد شد،

و هر کس هزار آیه بخواند، یک قنطار از نیکی برای او نوشته می‌شود. قنطار پانزده هزار (پنجاه هزار) مثقال است، بیست و چهار قیراط، کوچکترینش مانند کوه احد است و بزرگترینش آنچه میان آسمان و زمین است.»""",
                        fontSize = 16.sp,
                        lineHeight = 32.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
