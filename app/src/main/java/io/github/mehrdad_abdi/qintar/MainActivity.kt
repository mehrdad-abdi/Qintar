package io.github.mehrdad_abdi.qintar

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import io.github.mehrdad_abdi.qintar.ui.designsystem.QintarTheme
import io.github.mehrdad_abdi.qintar.ui.navigation.QintarAppShell

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Theme, pure-black and numeral settings are wired here by the settings feature
        // (QintarTheme parameters, LocalNumeralStyle). Until then: follow the system.
        setContent {
            QintarTheme { QintarAppShell() }
        }
    }
}
