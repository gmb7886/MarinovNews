package com.marinov.news

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.DynamicColors

class MainActivity : AppCompatActivity() {

    private lateinit var coordinator: CoordinatorLayout
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Material You
        DynamicColors.applyToActivityIfAvailable(this)

        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)

        coordinator = findViewById(R.id.coordinator)
        bottomNav = findViewById(R.id.bottomNav)

        setupSystemBarsInsets()
        setupNavigation(savedInstanceState)
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
}