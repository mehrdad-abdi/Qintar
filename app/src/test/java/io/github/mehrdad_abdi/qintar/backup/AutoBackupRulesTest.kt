package io.github.mehrdad_abdi.qintar.backup

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import io.github.mehrdad_abdi.qintar.R
import io.github.mehrdad_abdi.qintar.data.prefs.DataStorePrefsStore
import io.github.mehrdad_abdi.qintar.data.quran.QuranDatabase
import io.github.mehrdad_abdi.qintar.data.user.QintarDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/**
 * Android Auto Backup keeps the user's data and nothing that can be rebuilt. The rules name files
 * by path, so this fails if a file is renamed without the rules following.
 */
@RunWith(AndroidJUnit4::class)
class AutoBackupRulesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    init {
        // The rebuilt-from-the-APK Quran text must not be among the included files.
        check(QuranDatabase.FILE_NAME != QintarDatabase.FILE_NAME)
    }

    private val expectedIncludes = setOf(
        "database:${QintarDatabase.FILE_NAME}",
        "database:${QintarDatabase.FILE_NAME}-wal",
        "file:datastore/${DataStorePrefsStore.FILE_NAME}.preferences_pb",
    )

    /** (section, include/exclude) -> "domain:path" of every rule in the file. */
    private fun rules(xml: Int): Map<Pair<String?, String>, Set<String>> {
        val parser = context.resources.getXml(xml)
        val out = HashMap<Pair<String?, String>, MutableSet<String>>()
        var section: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "cloud-backup", "device-transfer" -> section = parser.name
                "include", "exclude" -> {
                    val rule = "${parser.getAttributeValue(null, "domain")}:${parser.getAttributeValue(null, "path")}"
                    out.getOrPut(section to parser.name) { HashSet() } += rule
                }
            }
        }
        return out
    }

    @Test
    fun androidTwelveAndLaterBackUpAndTransferTheUserDataOnly() {
        val rules = rules(R.xml.data_extraction_rules)
        for (section in listOf("cloud-backup", "device-transfer")) {
            // Include rules only: everything else, the Quran text among it, is left out.
            assertEquals(section, expectedIncludes, rules[section to "include"])
            assertEquals(section, null, rules[section to "exclude"])
        }
    }

    @Test
    fun olderVersionsBackUpTheSame() {
        val rules = rules(R.xml.backup_rules)
        assertEquals(expectedIncludes, rules[null to "include"])
        assertEquals(null, rules[null to "exclude"])
    }
}
