package dev.magisk.tiktokregionspoof

import android.app.Application
import com.google.android.material.color.DynamicColors

class RegionSpoofApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
