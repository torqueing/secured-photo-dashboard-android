package com.photodashboard.app.data

import com.photodashboard.app.BuildConfig
import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
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

    fun create(
        baseUrl: String,
        client: OkHttpClient,
        token: () -> String?,
        onUnauthorized: () -> Unit = {},
        onPasswordChangeRequired: () -> Unit = {},
    ): PhotoApi {
        val auth = Interceptor { chain ->
            val req = chain.request()
            val t = token() ?: return@Interceptor chain.proceed(req)
            val resp = chain.proceed(req.newBuilder().header("Authorization", "Bearer $t").build())
            when {
                resp.code == 401 && !req.url.encodedPath.endsWith("/auth/logout") -> onUnauthorized()
                resp.code == 403 && resp.peekBody(2048).string().contains("password_change_required") ->
                    onPasswordChangeRequired()
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
