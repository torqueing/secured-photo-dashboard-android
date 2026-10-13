package com.photodashboard.app.data

import com.photodashboard.app.BuildConfig
import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface PhotoApi {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("auth/mfa/verify")
    suspend fun verifyMfa(@Body body: MfaVerifyRequest): AuthResponse

    @POST("auth/logout")
    suspend fun logout()

    @GET("me")
    suspend fun me(): UserDto

    @POST("auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): ChangePasswordResponse

    @POST("photos/upload-url")
    suspend fun uploadUrl(@Body body: UploadUrlRequest): UploadUrlResponse

    @POST("photos")
    suspend fun registerPhoto(@Body body: RegisterPhotoRequest): PhotoDto

    @GET("photos")
    suspend fun photos(@Query("cursor") cursor: String? = null, @Query("limit") limit: Int = 100): PhotoPage

    @DELETE("photos/{id}")
    suspend fun deletePhoto(@Path("id") id: String)
}

object ApiFactory {
    val moshi: Moshi = Moshi.Builder().build()

    fun httpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Access and refresh tokens as stored on the device. */
    interface TokenStore {
        val accessToken: String?
        val refreshToken: String?
        fun saveTokens(access: String, refresh: String)
        fun signedOut()
        fun accountActionRequired(code: String)
    }

    private val refreshLock = Any()

    fun create(baseUrl: String, client: OkHttpClient, tokens: TokenStore): PhotoApi {
        val root = baseUrl.trimEnd('/') + "/"
        val refreshAdapter = moshi.adapter(AuthResponse::class.java)
        val refreshRequestAdapter = moshi.adapter(RefreshRequest::class.java)

        /** Exchange the refresh token for new tokens; returns the new access token or null. */
        fun refresh(staleAccess: String): String? = synchronized(refreshLock) {
            // Another request may already have refreshed while we waited.
            tokens.accessToken?.takeIf { it != staleAccess }?.let { return it }
            val refreshToken = tokens.refreshToken ?: return null
            val body = refreshRequestAdapter.toJson(RefreshRequest(refreshToken))
                .toRequestBody("application/json".toMediaType())
            client.newCall(Request.Builder().url(root + "auth/refresh").post(body).build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val parsed = resp.body?.string()?.let { refreshAdapter.fromJson(it) } ?: return null
                val access = parsed.accessToken ?: return null
                tokens.saveTokens(access, parsed.refreshToken ?: refreshToken)
                return access
            }
        }

        val auth = Interceptor { chain ->
            val req = chain.request()
            val t = tokens.accessToken ?: return@Interceptor chain.proceed(req)
            var resp = chain.proceed(req.newBuilder().header("Authorization", "Bearer $t").build())
            if (resp.code == 401 && resp.peekBody(2048).string().contains("token_expired")) {
                val fresh = refresh(t)
                if (fresh != null) {
                    resp.close()
                    resp = chain.proceed(req.newBuilder().header("Authorization", "Bearer $fresh").build())
                }
            }
            if (resp.code == 401 && !req.url.encodedPath.endsWith("/auth/logout")) {
                // Revoked, expired beyond refresh, or signed out elsewhere.
                tokens.signedOut()
            } else if (resp.code == 403) {
                val text = resp.peekBody(2048).string()
                listOf("password_change_required", "mfa_setup_required").firstOrNull { it in text }
                    ?.let { tokens.accountActionRequired(it) }
            }
            resp
        }
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
        }
        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client.newBuilder().addInterceptor(auth).addInterceptor(logging).build())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PhotoApi::class.java)
    }
}

/** Human-readable message for an API failure, using the `{"error": {...}}` body when present. */
fun Throwable.apiMessage(): String {
    if (this is HttpException) {
        val body = response()?.errorBody()?.string()
        val parsed = body?.let {
            runCatching { ApiFactory.moshi.adapter(ApiErrorBody::class.java).fromJson(it) }.getOrNull()
        }
        return parsed?.error?.message ?: "HTTP ${code()}"
    }
    return message ?: javaClass.simpleName
}
