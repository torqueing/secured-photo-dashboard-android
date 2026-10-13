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
    /** Admins must turn on two-factor (on the web dashboard) before using the app. */
    val mfaSetupRequired: Boolean = false,
)

/**
 * Session tokens and settings (AND-2, AND-14). The 15-minute access token and the rotating
 * refresh token are kept in DataStore; encrypting them with a Keystore key is part of S4.
 */
class SessionStore(private val context: Context) {
    private object Keys {
        val token = stringPreferencesKey("token")
        val userId = longPreferencesKey("user_id")
        val email = stringPreferencesKey("email")
        val displayName = stringPreferencesKey("display_name")
        val orgId = longPreferencesKey("org_id")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val mustChangePassword = booleanPreferencesKey("must_change_password")
        val mfaSetupRequired = booleanPreferencesKey("mfa_setup_required")
        val refreshToken = stringPreferencesKey("refresh_token")
    }

    @Volatile
    var cachedToken: String? = null
        private set

    @Volatile
    var cachedRefreshToken: String? = null
        private set

    val session: Flow<Session?> = context.dataStore.data.map { p ->
        val token = p[Keys.token]
        cachedToken = token
        cachedRefreshToken = p[Keys.refreshToken]
        if (token == null) null else Session(
            token, p[Keys.userId] ?: 0, p[Keys.email].orEmpty(), p[Keys.displayName].orEmpty(), p[Keys.orgId],
            p[Keys.mustChangePassword] ?: false, p[Keys.mfaSetupRequired] ?: false,
        )
    }

    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { it[Keys.wifiOnly] ?: false }

    suspend fun current(): Session? = session.first()

    suspend fun save(auth: AuthResponse) {
        val access = requireNotNull(auth.accessToken) { "no access token" }
        val user = requireNotNull(auth.user) { "no user" }
        cachedToken = access
        cachedRefreshToken = auth.refreshToken
        context.dataStore.edit {
            it[Keys.token] = access
            auth.refreshToken?.let { r -> it[Keys.refreshToken] = r }
            it[Keys.userId] = user.id
            it[Keys.email] = user.email
            // Shown as "First Last".
            it[Keys.displayName] = listOf(user.firstName, user.lastName).filter { n -> n.isNotBlank() }
                .joinToString(" ").ifBlank { user.displayName }
            val orgId = user.orgId
            if (orgId == null) it.remove(Keys.orgId) else it[Keys.orgId] = orgId
            it[Keys.mustChangePassword] = user.mustChangePassword || user.accountAction == "password_change_required"
            it[Keys.mfaSetupRequired] = user.accountAction == "mfa_setup_required"
        }
    }

    suspend fun saveTokens(access: String, refresh: String) {
        cachedToken = access
        cachedRefreshToken = refresh
        context.dataStore.edit {
            if (it[Keys.token] != null) {
                it[Keys.token] = access
                it[Keys.refreshToken] = refresh
            }
        }
    }

    suspend fun setMfaSetupRequired(value: Boolean) {
        context.dataStore.edit { it[Keys.mfaSetupRequired] = value }
    }

    suspend fun clear() {
        cachedToken = null
        cachedRefreshToken = null
        context.dataStore.edit {
            it.remove(Keys.token); it.remove(Keys.refreshToken); it.remove(Keys.userId); it.remove(Keys.email)
            it.remove(Keys.displayName); it.remove(Keys.orgId); it.remove(Keys.mustChangePassword)
            it.remove(Keys.mfaSetupRequired)
        }
    }

    suspend fun setMustChangePassword(value: Boolean) {
        context.dataStore.edit { it[Keys.mustChangePassword] = value }
    }

    suspend fun setWifiOnly(value: Boolean) {
        context.dataStore.edit { it[Keys.wifiOnly] = value }
    }
}
