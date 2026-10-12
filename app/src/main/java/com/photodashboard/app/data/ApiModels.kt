package com.photodashboard.app.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val email: String,
    val password: String,
    val username: String,
    @param:Json(name = "first_name") val firstName: String,
    @param:Json(name = "last_name") val lastName: String,
)

@JsonClass(generateAdapter = true)
data class LoginRequest(val email: String, val password: String)

@JsonClass(generateAdapter = true)
data class ChangePasswordRequest(
    @param:Json(name = "current_password") val currentPassword: String,
    @param:Json(name = "new_password") val newPassword: String,
)

@JsonClass(generateAdapter = true)
data class ChangePasswordResponse(
    val ok: Boolean,
    @param:Json(name = "other_sessions_signed_out") val otherSessionsSignedOut: Int,
)

@JsonClass(generateAdapter = true)
data class UserDto(
    val id: Long,
    val email: String,
    @param:Json(name = "display_name") val displayName: String,
    val username: String = "",
    @param:Json(name = "first_name") val firstName: String = "",
    @param:Json(name = "last_name") val lastName: String = "",
    val role: String,
    /** Null until a platform admin or an invite puts the user in an organisation. */
    @param:Json(name = "org_id") val orgId: Long?,
    @param:Json(name = "org_name") val orgName: String?,
    /** Set by an org admin: the user must choose a new password before anything else. */
    @param:Json(name = "must_change_password") val mustChangePassword: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class AuthResponse(val token: String, val user: UserDto)

@JsonClass(generateAdapter = true)
data class UploadUrlRequest(@param:Json(name = "photo_id") val photoId: String?)

@JsonClass(generateAdapter = true)
data class UploadUrlResponse(
    @param:Json(name = "photo_id") val photoId: String,
    @param:Json(name = "s3_key") val s3Key: String,
    @param:Json(name = "upload_url") val uploadUrl: String,
    @param:Json(name = "public_url") val publicUrl: String,
    val status: String,
)

@JsonClass(generateAdapter = true)
data class RegisterPhotoRequest(
    @param:Json(name = "photo_id") val photoId: String,
    @param:Json(name = "s3_key") val s3Key: String,
    @param:Json(name = "captured_at") val capturedAt: String?,
    val latitude: Double?,
    val longitude: Double?,
    @param:Json(name = "altitude_m") val altitudeM: Double?,
    @param:Json(name = "accuracy_m") val accuracyM: Float?,
    @param:Json(name = "bearing_deg") val bearingDeg: Float?,
    @param:Json(name = "speed_mps") val speedMps: Float?,
    @param:Json(name = "location_source") val locationSource: String?,
    @param:Json(name = "device_make") val deviceMake: String?,
    @param:Json(name = "device_model") val deviceModel: String?,
    val width: Int?,
    val height: Int?,
    @param:Json(name = "size_bytes") val sizeBytes: Long?,
)

@JsonClass(generateAdapter = true)
data class PhotoDto(
    val id: String,
    @param:Json(name = "s3_key") val s3Key: String,
    @param:Json(name = "public_url") val publicUrl: String,
    val status: String,
    @param:Json(name = "captured_at") val capturedAt: String?,
    val latitude: Double?,
    val longitude: Double?,
    @param:Json(name = "altitude_m") val altitudeM: Double?,
    @param:Json(name = "accuracy_m") val accuracyM: Double?,
    @param:Json(name = "device_model") val deviceModel: String?,
)

@JsonClass(generateAdapter = true)
data class PhotoPage(val items: List<PhotoDto>, @param:Json(name = "next_cursor") val nextCursor: String?)

@JsonClass(generateAdapter = true)
data class ApiErrorBody(val error: ApiErrorDetail?)

@JsonClass(generateAdapter = true)
data class ApiErrorDetail(val code: String?, val message: String?)
