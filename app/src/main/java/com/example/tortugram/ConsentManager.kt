package com.example.tortugram

import android.content.Context

/**
 * Registra si el usuario aceptó «Privacy & Data». MainActivity lo consulta para decidir si
 * muestra ConsentScreen antes del login.
 *
 * isaac-maker 2026
 */
object ConsentManager {

    private const val PREFS_NAME = "tortugram_consent"

    // Versión de la política: incrementarla exige una nueva aceptación.
    private const val CONSENT_VERSION = 1
    private const val KEY_AGREED_VERSION = "agreed_version"

    fun hasAgreed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_AGREED_VERSION, 0) >= CONSENT_VERSION
    }

    fun setAgreed(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_AGREED_VERSION, CONSENT_VERSION).apply()
    }
}
