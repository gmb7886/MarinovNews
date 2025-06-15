package com.marinov.news

import android.app.Application
import android.os.Build
import com.google.android.material.color.DynamicColors

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isDynamicColorsSupported()) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
    }

    private fun isDynamicColorsSupported(): Boolean {
        val excludedManufacturers = setOf(
            "xiaomi",
            "huawei",
            "oppo",
            "realme",
            "vivo",
            "oneplus"
        )

        return !excludedManufacturers.contains(Build.MANUFACTURER.lowercase())
    }
}