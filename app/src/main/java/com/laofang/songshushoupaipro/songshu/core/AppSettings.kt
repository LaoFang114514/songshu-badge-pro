package com.laofang.songshushoupaipro.songshu.core

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import org.json.JSONObject

data class AppSettings(
    val keepScreenOn: Boolean = false,
    val flipDisplay: Boolean = false,
    val showBattery: Boolean = false,
    val antiBurnIn: Boolean = false,
    val longPressCopy: Boolean = false,
    val themeColorIndex: Int = 0,
    val darkMode: Int = 0
)

object SettingsManager {
    private const val PREFS_NAME = "app_settings"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSettings(context: Context): AppSettings {
        val p = prefs(context)
        if (!p.getBoolean("has_initialized_theme", false)) {
            val defaultIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 8 else 0
            p.edit {
                putInt("theme_color_index", defaultIndex)
                putInt("dark_mode", 0)
                putBoolean("has_initialized_theme", true)
            }
            return AppSettings(themeColorIndex = defaultIndex)
        }
        return AppSettings(
            keepScreenOn = p.getBoolean("keep_screen_on", false),
            flipDisplay = p.getBoolean("flip_display", false),
            showBattery = p.getBoolean("show_battery", false),
            antiBurnIn = p.getBoolean("anti_burn_in", false),
            longPressCopy = p.getBoolean("long_press_copy", false),
            themeColorIndex = p.getInt("theme_color_index", 0),
            darkMode = p.getInt("dark_mode", 0)
        )
    }

    fun saveSettings(context: Context, s: AppSettings) {
        prefs(context).edit {
            putBoolean("keep_screen_on", s.keepScreenOn)
            putBoolean("flip_display", s.flipDisplay)
            putBoolean("show_battery", s.showBattery)
            putBoolean("anti_burn_in", s.antiBurnIn)
            putBoolean("long_press_copy", s.longPressCopy)
            putInt("theme_color_index", s.themeColorIndex)
            putInt("dark_mode", s.darkMode)
            putBoolean("has_initialized_theme", true)
        }
    }

    fun exportJson(context: Context): JSONObject = JSONObject().apply {
        val s = loadSettings(context)
        put("keep_screen_on", s.keepScreenOn)
        put("flip_display", s.flipDisplay)
        put("show_battery", s.showBattery)
        put("anti_burn_in", s.antiBurnIn)
        put("long_press_copy", s.longPressCopy)
        put("theme_color_index", s.themeColorIndex)
        put("dark_mode", s.darkMode)
    }

    fun importJson(context: Context, obj: JSONObject) {
        val s = loadSettings(context)
        saveSettings(
            context,
            s.copy(
                keepScreenOn = obj.optBoolean("keep_screen_on", s.keepScreenOn),
                flipDisplay = obj.optBoolean("flip_display", s.flipDisplay),
                showBattery = obj.optBoolean("show_battery", s.showBattery),
                antiBurnIn = obj.optBoolean("anti_burn_in", s.antiBurnIn),
                longPressCopy = obj.optBoolean("long_press_copy", s.longPressCopy),
                themeColorIndex = obj.optInt("theme_color_index", s.themeColorIndex),
                darkMode = obj.optInt("dark_mode", s.darkMode)
            )
        )
    }
}
