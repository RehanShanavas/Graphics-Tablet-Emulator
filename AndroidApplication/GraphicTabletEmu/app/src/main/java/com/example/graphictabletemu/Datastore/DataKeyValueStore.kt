package com.example.graphictabletemu.Datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension property for DataStore
private val Context.datastore: DataStore<Preferences> by preferencesDataStore("SettingsStore")

class DataKeyValueStore(private val context: Context) {

    private object PreferenceKeys {
        val FrameAspectRatio = floatPreferencesKey("aspectRatio")
        val FrameScale = intPreferencesKey("scale")
        val IgnoreFinger = booleanPreferencesKey("ignoreFinger")
    }

    // Update values
    suspend fun setAspectRatio(value: Float) {
        context.datastore.edit { settings ->
            settings[PreferenceKeys.FrameAspectRatio] = value
        }
    }

    suspend fun setFrameScale(value: Int) {
        context.datastore.edit { settings ->
            settings[PreferenceKeys.FrameScale] = value
        }
    }

    suspend fun setIgnoreFinger(value: Boolean) {
        context.datastore.edit { settings ->
            settings[PreferenceKeys.IgnoreFinger] = value
        }
    }

    // Read values as Flow (reactive)
    val aspectRatio: Flow<Float?> = context.datastore.data.map { prefs ->
        prefs[PreferenceKeys.FrameAspectRatio] ?: (1280f / 720)
    }

    val frameScale: Flow<Int?> = context.datastore.data.map { prefs ->
        prefs[PreferenceKeys.FrameScale] ?: 100
    }

    val ignoreFinger: Flow<Boolean?> = context.datastore.data.map { prefs ->
        prefs[PreferenceKeys.IgnoreFinger] ?: false
    }
}
