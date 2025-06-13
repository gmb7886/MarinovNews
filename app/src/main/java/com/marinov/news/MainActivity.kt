package com.marinov.news;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.DynamicColors;

public class MainActivity extends AppCompatActivity {

    private CoordinatorLayout coordinator;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Material You
        DynamicColors.applyToActivityIfAvailable(this);

        // Edge-to-edge manual
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_main);

        coordinator = findViewById(R.id.coordinator);
        bottomNav   = findViewById(R.id.bottomNav);

        setupSystemBarsInsets();
        setupNavigation(savedInstanceState);
    }

    private void setupSystemBarsInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(coordinator, (view, insets) -> {
            int statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navBarInset    = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;

            // Aplica padding top ao fragmentContainer
            findViewById(R.id.fragmentContainer)
                    .setPadding(0, statusBarInset, 0, 0);

            // Aplica padding bottom ao BottomNavigationView
            bottomNav.setPadding(
                    bottomNav.getPaddingLeft(),
                    bottomNav.getPaddingTop(),
                    bottomNav.getPaddingRight(),
                    navBarInset
            );

            // Consumir todos os insets
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupNavigation(Bundle savedInstanceState) {
        bottomNav.setOnItemSelectedListener(item -> {
            handleNavigation(item.getItemId());
            return true;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.navigation_home);
        }
    }

    private void handleNavigation(int itemId) {
        Fragment selected = null;
        if (itemId == R.id.navigation_home) {
            selected = new FeedFragment();
        } else if (itemId == R.id.navigation_settings) {
            selected = new SettingsFragment();
        }
        if (selected != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, selected)
                    .commit();
        }
    }
}
