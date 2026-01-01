package com.example.clinometer

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object DragStorage {
    private const val PREFS_NAME = "drag_sessions_prefs"
    private const val KEY_SESSIONS = "drag_sessions"

    fun saveDragSessions(context: Context, sessions: List<DragSession>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = gson.toJson(sessions)
        prefs.edit().putString(KEY_SESSIONS, json).apply()

        // Debug log
        println("✅ Saved ${sessions.size} sessions")
    }

    fun loadDragSessions(context: Context): MutableList<DragSession> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SESSIONS, null)

        return if (json != null) {
            try {
                val gson = Gson()
                val type = object : TypeToken<MutableList<DragSession>>() {}.type
                val sessions = gson.fromJson<MutableList<DragSession>>(json, type)
                println("✅ Loaded ${sessions?.size ?: 0} sessions")
                sessions ?: mutableListOf()
            } catch (e: Exception) {
                println("❌ Error loading sessions: ${e.message}")
                e.printStackTrace()
                mutableListOf()
            }
        } else {
            println("ℹ️ No sessions found in storage")
            mutableListOf()
        }
    }

    fun addDragSession(context: Context, session: DragSession) {
        val sessions = loadDragSessions(context).toMutableList()
        sessions.add(session)
        saveDragSessions(context, sessions)
        println("✅ Added session: ${session.name} (ID: ${session.id}, Profile: ${session.profileId})")
    }

    fun updateDragSession(context: Context, sessionId: Long, updatedSession: DragSession) {
        val sessions = loadDragSessions(context).toMutableList()
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index != -1) {
            sessions[index] = updatedSession
            saveDragSessions(context, sessions)
            println("✅ Updated session: ${updatedSession.name}")
        } else {
            println("❌ Session with ID $sessionId not found for update")
        }
    }

    fun deleteDragSession(context: Context, sessionId: Long) {
        val sessions = loadDragSessions(context).toMutableList()
        val initialSize = sessions.size
        val removed = sessions.removeAll { it.id == sessionId }

        if (removed) {
            saveDragSessions(context, sessions)
            println("✅ Deleted session with ID: $sessionId")
        } else {
            println("❌ Session with ID $sessionId not found for deletion")
        }
    }

    fun getDragSession(context: Context, sessionId: Long): DragSession? {
        return loadDragSessions(context).find { it.id == sessionId }
    }
    
    fun getAllDragSessions(context: Context): List<DragSession> {
        return loadDragSessions(context)
    }
}