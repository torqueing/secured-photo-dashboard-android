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
data class LoginRequest(
    val email: String,
    val password: String,
    @param:Json(name = "device_label") val deviceLabel: String? = null,
)

@JsonClass(generateAdapter = true)
data class MfaVerifyRequest(
    @param:Json(name = "mfa_token") val mfaToken: String,
    val code: String,
    @param:Json(name = "device_label") val deviceLabel: String? = null,
)

@JsonClass(generateAdapter = true)
data class RefreshRequest(@param:Json(name = "refresh_token") val refreshToken: String)

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
    @param:Json(name = "totp_enabled") val totpEnabled: Boolean = false,
    /** password_change_required, mfa_setup_required, or null when the account is ready. */
    @param:Json(name = "account_action") val accountAction: String? = null,
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @param:Json(name = "access_token") val accessToken: String? = null,
    @param:Json(name = "refresh_token") val refreshToken: String? = null,
    val user: UserDto? = null,
    /** Sign-in needs a second step: post [mfaToken] and a TOTP code to auth/mfa/verify. */
    @param:Json(name = "mfa_required") val mfaRequired: Boolean = false,
    @param:Json(name = "mfa_token") val mfaToken: String? = null,
)

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
    /** 1 for sealed (.spd) photos; null for legacy plain JPEGs. */
    @param:Json(name = "enc_version") val encVersion: Int? = null,
)

@JsonClass(generateAdapter = true)
data class PhotoPage(val items: List<PhotoDto>, @param:Json(name = "next_cursor") val nextCursor: String?)

@JsonClass(generateAdapter = true)
data class ApiErrorBody(val error: ApiErrorDetail?)

@JsonClass(generateAdapter = true)
data class ApiErrorDetail(val code: String?, val message: String?)

// ---------------------------------------------------------------- S4: keys and sealed photos
@JsonClass(generateAdapter = true)
data class RecoveryKeyDto(
    @param:Json(name = "key_id") val keyId: String,
    @param:Json(name = "public_key_spki_b64") val publicKeySpkiB64: String,
)

@JsonClass(generateAdapter = true)
data class OrgKeyResponse(
    @param:Json(name = "key_arn") val keyArn: String,
    @param:Json(name = "public_key_spki_b64") val publicKeySpkiB64: String,
    val recovery: RecoveryKeyDto? = null,
)

@JsonClass(generateAdapter = true)
data class DeviceKeyRequest(
    @param:Json(name = "public_key_spki_b64") val publicKeySpkiB64: String,
    @param:Json(name = "attestation_chain_b64") val attestationChainB64: List<String>,
)

@JsonClass(generateAdapter = true)
data class DeviceKeyResponse(@param:Json(name = "device_key_id") val deviceKeyId: String)

@JsonClass(generateAdapter = true)
data class SealedUploadUrlsRequest(
    @param:Json(name = "photo_id") val photoId: String,
    @param:Json(name = "photo_sha256_b64") val photoSha256B64: String,
    @param:Json(name = "thumb_sha256_b64") val thumbSha256B64: String,
)

@JsonClass(generateAdapter = true)
data class UploadTarget(@param:Json(name = "s3_key") val s3Key: String, @param:Json(name = "upload_url") val uploadUrl: String)

@JsonClass(generateAdapter = true)
data class SealedUploadUrlsResponse(
    @param:Json(name = "photo_id") val photoId: String,
    val status: String,
    val photo: UploadTarget,
    val thumbnail: UploadTarget,
)

@JsonClass(generateAdapter = true)
data class RegisterSealedRequest(
    @param:Json(name = "photo_id") val photoId: String,
    val sha256: String,
    @param:Json(name = "signed_payload") val signedPayload: String,
    val signature: String,
    @param:Json(name = "device_key_id") val deviceKeyId: String,
    @param:Json(name = "key_arn") val keyArn: String,
    @param:Json(name = "wrapped_key") val wrappedKey: String,
    @param:Json(name = "captured_at") val capturedAt: String,
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
)
