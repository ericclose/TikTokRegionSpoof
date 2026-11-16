package dev.magisk.tiktokregionspoof

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import dev.magisk.tiktokregionspoof.model.SpoofConfigStore

class RegionSpoofApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(SpoofConfigStore.loadThemeMode(this).nightMode)
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
