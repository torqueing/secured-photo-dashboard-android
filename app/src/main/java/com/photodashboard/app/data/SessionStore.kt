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
import com.photodashboard.app.crypto.DeviceKeys
import com.photodashboard.app.crypto.OrgKey

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
class SessionStore(private val context: Context, private val keys: DeviceKeys) {
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
        val tokenEnc = stringPreferencesKey("token_enc")
        val refreshTokenEnc = stringPreferencesKey("refresh_token_enc")
        val orgKeyArn = stringPreferencesKey("org_key_arn")
        val orgPublicKey = stringPreferencesKey("org_public_key")
        val recoveryKeyId = stringPreferencesKey("recovery_key_id")
        val recoveryPublicKey = stringPreferencesKey("recovery_public_key")
        val registeredDeviceKey = stringPreferencesKey("registered_device_key") // pre-per-user marker, cleared on use
    }

    // Tokens are stored encrypted with a Keystore key; plain values from older versions are read once.
    private fun readToken(p: Preferences, enc: Preferences.Key<String>, plain: Preferences.Key<String>): String? =
        p[enc]?.let { keys.decryptToken(it) } ?: p[plain]

    @Volatile
    var cachedToken: String? = null
        private set

    @Volatile
    var cachedRefreshToken: String? = null
        private set

    val session: Flow<Session?> = context.dataStore.data.map { p ->
        val token = readToken(p, Keys.tokenEnc, Keys.token)
        cachedToken = token
        cachedRefreshToken = readToken(p, Keys.refreshTokenEnc, Keys.refreshToken)
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
            it[Keys.tokenEnc] = keys.encryptToken(access)
            it.remove(Keys.token)
            it.remove(Keys.refreshToken)
            auth.refreshToken?.let { r -> it[Keys.refreshTokenEnc] = keys.encryptToken(r) }
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
            if (it[Keys.tokenEnc] != null || it[Keys.token] != null) {
                it[Keys.tokenEnc] = keys.encryptToken(access)
                it[Keys.refreshTokenEnc] = keys.encryptToken(refresh)
                it.remove(Keys.token)
                it.remove(Keys.refreshToken)
            }
        }
    }

    suspend fun orgKey(): OrgKey? = context.dataStore.data.first().let { p ->
        val arn = p[Keys.orgKeyArn] ?: return@let null
        OrgKey(arn, p[Keys.orgPublicKey] ?: return@let null, p[Keys.recoveryKeyId], p[Keys.recoveryPublicKey])
    }

    suspend fun saveOrgKey(key: OrgKeyResponse) {
        context.dataStore.edit {
            it[Keys.orgKeyArn] = key.keyArn
            it[Keys.orgPublicKey] = key.publicKeySpkiB64
            val r = key.recovery
            if (r == null) { it.remove(Keys.recoveryKeyId); it.remove(Keys.recoveryPublicKey) } else {
                it[Keys.recoveryKeyId] = r.keyId
                it[Keys.recoveryPublicKey] = r.publicKeySpkiB64
            }
        }
    }

    // One signing key per account on this phone, so the "registered" marker is per account too.
    private fun registeredKeyFor(userId: Long) = stringPreferencesKey("registered_device_key_u$userId")

    suspend fun registeredDeviceKey(userId: Long): String? = context.dataStore.data.first()[registeredKeyFor(userId)]

    suspend fun setRegisteredDeviceKey(userId: Long, id: String) {
        context.dataStore.edit { it.remove(Keys.registeredDeviceKey); it[registeredKeyFor(userId)] = id }
    }

    suspend fun setMfaSetupRequired(value: Boolean) {
        context.dataStore.edit { it[Keys.mfaSetupRequired] = value }
    }

    suspend fun clear() {
        cachedToken = null
        cachedRefreshToken = null
        context.dataStore.edit {
            it.remove(Keys.token); it.remove(Keys.refreshToken); it.remove(Keys.userId); it.remove(Keys.email)
            it.remove(Keys.tokenEnc); it.remove(Keys.refreshTokenEnc); it.remove(Keys.orgKeyArn); it.remove(Keys.orgPublicKey)
            it.remove(Keys.recoveryKeyId); it.remove(Keys.recoveryPublicKey)
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
