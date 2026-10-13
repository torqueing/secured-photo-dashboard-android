package com.photodashboard.app

import android.content.Context
import com.photodashboard.app.data.ApiFactory
import com.photodashboard.app.data.PhotoApi
import com.photodashboard.app.data.PhotoDb
import com.photodashboard.app.data.PhotoRepository
import com.photodashboard.app.data.SessionStore
import com.photodashboard.app.location.LocationSource
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    val session = SessionStore(context)
    val http: OkHttpClient = ApiFactory.httpClient()
    val api: PhotoApi = ApiFactory.create(
        BuildConfig.API_BASE_URL.ifBlank { "https://api.invalid/v1" },
        http,
        object : ApiFactory.TokenStore {
            override val accessToken get() = session.cachedToken
            override val refreshToken get() = session.cachedRefreshToken
            override fun saveTokens(access: String, refresh: String) = runBlocking { session.saveTokens(access, refresh) }

            // The server revoked this session (signed out elsewhere, forced password change, deleted).
            override fun signedOut() = runBlocking { session.clear() }

            override fun accountActionRequired(code: String) = runBlocking {
                if (code == "password_change_required") session.setMustChangePassword(true)
                else session.setMfaSetupRequired(true)
            }
        },
    )
    val db = PhotoDb.create(context)
    val repository = PhotoRepository(context, db, api, session)
    val location = LocationSource(context)
}
