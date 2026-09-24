package com.example.tortugram

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bloqueo local de chats, independiente de Telegram y de la sesión. Se persiste en
 * SharedPreferences y se usa desde ReportScreen para ocultar chats en HomeScreen.
 *
 * isaac-maker 2026
 */
object BlockedChatsManager {

    private const val PREFS_NAME = "tortugram_blocked_chats"
    private const val KEY_IDS = "blocked_chat_ids"

    private val _blockedIds = MutableStateFlow<Set<Long>>(emptySet())
    val blockedIds: StateFlow<Set<Long>> = _blockedIds.asStateFlow()

    @Volatile private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        val stored = prefs(context).getStringSet(KEY_IDS, emptySet()) ?: emptySet()
        _blockedIds.value = stored.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun isBlocked(chatId: Long): Boolean = chatId in _blockedIds.value

    fun block(context: Context, chatId: Long) {
        val updated = _blockedIds.value + chatId
        _blockedIds.value = updated
        persist(context, updated)
    }

    fun unblock(context: Context, chatId: Long) {
        val updated = _blockedIds.value - chatId
        _blockedIds.value = updated
        persist(context, updated)
    }

    private fun persist(context: Context, ids: Set<Long>) {
        prefs(context)
            .edit()
            .putStringSet(KEY_IDS, ids.map { it.toString() }.toSet())
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
