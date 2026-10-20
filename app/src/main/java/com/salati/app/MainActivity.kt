package com.salati.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.salati.app.domain.ThemeMode
import com.salati.app.notify.AlarmScheduler
import com.salati.app.ui.AppViewModel
import com.salati.app.ui.CalendarViewModel
import com.salati.app.ui.MainScaffold
import com.salati.app.ui.QadaaViewModel
import com.salati.app.ui.SetupScreen
import com.salati.app.ui.StatsViewModel
import com.salati.app.ui.TodayViewModel
import com.salati.app.ui.theme.SalatiTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val factory by lazy {
        val app = application as SalatiApp
        viewModelFactory {
            initializer { AppViewModel(app.repository, app) }
            initializer { TodayViewModel(app.repository) }
            initializer { QadaaViewModel(app.repository) }
            initializer { CalendarViewModel(app.repository) }
            initializer { StatsViewModel(app.repository) }
        }
    }
    private val appVm: AppViewModel by viewModels { factory }
    private val todayVm: TodayViewModel by viewModels { factory }
    private val qadaaVm: QadaaViewModel by viewModels { factory }
    private val calendarVm: CalendarViewModel by viewModels { factory }
    private val statsVm: StatsViewModel by viewModels { factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SalatiApp

        // Keep the per-app language in sync with the saved setting (AppCompat handles RTL + recreation).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.repository.settings.collect { s ->
                    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                    if (!current.startsWith(s.language)) {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(s.language))
                    }
                }
            }
        }

        setContent {
            val settings by appVm.settingsState.collectAsStateWithLifecycle()
            SalatiTheme(settings?.themeMode() ?: ThemeMode.SYSTEM) {
                Surface(Modifier.fillMaxSize()) {
                    val s = settings
                    when {
                        s == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        !s.setupComplete -> SetupScreen(appVm, s)
                        else -> MainScaffold(appVm, todayVm, qadaaVm, calendarVm, statsVm)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val app = application as SalatiApp
        lifecycleScope.launch {
            runCatching { app.repository.runAutoMiss() }
            runCatching { AlarmScheduler.rescheduleAll(this@MainActivity, app.repository) }
        }
    }
}
