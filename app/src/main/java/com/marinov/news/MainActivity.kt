package com.marinov.news

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.MaterialColors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var coordinator: CoordinatorLayout
    lateinit var bottomNav: BottomNavigationView // Tornar público para acesso do fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBarsForLegacyDevices()
        MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorPrimaryContainer,
            Color.BLACK
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        coordinator = findViewById(R.id.coordinator)
        bottomNav = findViewById(R.id.bottomNav)
        setupSystemBarsInsets()
        setupNavigation(savedInstanceState)
        iniciarUpdateWorker()
    }
    private fun configureSystemBarsForLegacyDevices() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val isDarkMode = when (AppCompatDelegate.getDefaultNightMode()) {
                AppCompatDelegate.MODE_NIGHT_YES -> true
                AppCompatDelegate.MODE_NIGHT_NO -> false
                else -> {
                    val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    currentNightMode == Configuration.UI_MODE_NIGHT_YES
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.apply {
                    clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                    addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                        statusBarColor = Color.BLACK
                        navigationBarColor = Color.BLACK

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            var flags = decorView.systemUiVisibility
                            flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                            decorView.systemUiVisibility = flags
                        }
                    } else {
                        navigationBarColor = if (isDarkMode) {
                            ContextCompat.getColor(this@MainActivity, R.color.nav_bar_dark)
                        } else {
                            ContextCompat.getColor(this@MainActivity, R.color.nav_bar_light)
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                var flags = window.decorView.systemUiVisibility

                if (isDarkMode) {
                    flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                } else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                    flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }

                window.decorView.systemUiVisibility = flags
            }

            if (!isDarkMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                var flags = window.decorView.systemUiVisibility
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                window.decorView.systemUiVisibility = flags
            }
        }
    }
    private fun iniciarUpdateWorker() {
        val updateWork = PeriodicWorkRequest.Builder(
            UpdateCheckWorker::class.java,
            15,
            TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "UpdateCheckWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            updateWork
        )
    }
    private fun setupSystemBarsInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(coordinator) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            // Aplica padding top ao fragmentContainer
            findViewById<android.view.View>(R.id.fragmentContainer)
                .setPadding(0, statusBarInset, 0, 0)

            // Aplica padding bottom ao BottomNavigationView
            bottomNav.setPadding(
                bottomNav.paddingLeft,
                bottomNav.paddingTop,
                bottomNav.paddingRight,
                navBarInset
            )

            // Consumir todos os insets
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupNavigation(savedInstanceState: Bundle?) {
        bottomNav.setOnItemSelectedListener { item ->
            handleNavigation(item.itemId)
            true
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.navigation_home
        }
    }

    private fun handleNavigation(itemId: Int) {
        val selected = when (itemId) {
            R.id.navigation_home -> FeedFragment()
            R.id.navigation_settings -> SettingsFragment()
            else -> null
        }

        selected?.let {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, it)
                .commit()
        }
    }

    // Métodos para controlar a visibilidade da barra
    fun hideBottomNavigation() {
        if (bottomNav.translationY == 0f) {
            bottomNav.animate().translationY(bottomNav.height.toFloat()).setDuration(300).start()
        }
    }

    fun showBottomNavigation() {
        if (bottomNav.translationY != 0f) {
            bottomNav.animate().translationY(0f).setDuration(300).start()
        }
    }
}