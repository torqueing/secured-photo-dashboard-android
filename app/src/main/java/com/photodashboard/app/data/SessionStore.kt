package com.photodashboard.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

data class Session(
    val token: String,
    val userId: Long,
    val email: String,
    val displayName: String,
    val orgId: Long?,
    val mustChangePassword: Boolean = false,
)

/** Bearer token and settings (AND-2, AND-14). v1 stores the token in plain DataStore; v2 moves it to Keystore. */
class SessionStore(private val context: Context) {
    private object Keys {
        val token = stringPreferencesKey("token")
        val userId = longPreferencesKey("user_id")
        val email = stringPreferencesKey("email")
        val displayName = stringPreferencesKey("display_name")
        val orgId = longPreferencesKey("org_id")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val mustChangePassword = booleanPreferencesKey("must_change_password")
    }

    @Volatile
    var cachedToken: String? = null
        private set

    val session: Flow<Session?> = context.dataStore.data.map { p ->
        val token = p[Keys.token]
        cachedToken = token
        if (token == null) null else Session(
            token, p[Keys.userId] ?: 0, p[Keys.email].orEmpty(), p[Keys.displayName].orEmpty(), p[Keys.orgId],
            p[Keys.mustChangePassword] ?: false,
        )
    }

    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { it[Keys.wifiOnly] ?: false }

    suspend fun current(): Session? = session.first()

    suspend fun save(auth: AuthResponse) {
        cachedToken = auth.token
        context.dataStore.edit {
            it[Keys.token] = auth.token
            it[Keys.userId] = auth.user.id
            it[Keys.email] = auth.user.email
            // Shown as "First Last".
            it[Keys.displayName] = listOf(auth.user.firstName, auth.user.lastName).filter { n -> n.isNotBlank() }
                .joinToString(" ").ifBlank { auth.user.displayName }
            val orgId = auth.user.orgId
            if (orgId == null) it.remove(Keys.orgId) else it[Keys.orgId] = orgId
            it[Keys.mustChangePassword] = auth.user.mustChangePassword
        }
    }

    suspend fun clear() {
        cachedToken = null
        context.dataStore.edit {
            it.remove(Keys.token); it.remove(Keys.userId); it.remove(Keys.email)
            it.remove(Keys.displayName); it.remove(Keys.orgId); it.remove(Keys.mustChangePassword)
        }
    }

    suspend fun setMustChangePassword(value: Boolean) {
        context.dataStore.edit { it[Keys.mustChangePassword] = value }
    }

    suspend fun setWifiOnly(value: Boolean) {
        context.dataStore.edit { it[Keys.wifiOnly] = value }
    }
}
