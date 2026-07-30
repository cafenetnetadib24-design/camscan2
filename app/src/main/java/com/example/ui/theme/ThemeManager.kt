package com.example.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppThemeMode {
    LIGHT, DARK, SYSTEM
}

object ThemeManager {
    private const val PREFS_NAME = "app_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    var currentThemeMode by mutableStateOf(AppThemeMode.LIGHT)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_THEME_MODE, AppThemeMode.LIGHT.name) ?: AppThemeMode.LIGHT.name
        currentThemeMode = try {
            AppThemeMode.valueOf(saved)
        } catch (e: Exception) {
            AppThemeMode.LIGHT
        }
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        currentThemeMode = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun toggleTheme(context: Context) {
        val newMode = if (currentThemeMode == AppThemeMode.DARK) AppThemeMode.LIGHT else AppThemeMode.DARK
        setThemeMode(context, newMode)
    }
}
