# Secured Photo Dashboard – Android

Kotlin + Jetpack Compose app that takes geotagged photos, writes the location into
EXIF, keeps a local gallery and uploads in the background to the Secured Photo Dashboard
backend (S3 presigned PUT + `POST /v1/photos`).

- targetSdk / compileSdk 37, minSdk 29
- CameraX, Fused Location Provider, androidx.exifinterface, Room, WorkManager,
  Retrofit/OkHttp/Moshi, Coil, DataStore

## Build

The API URL is never committed (this repository is public). Set it in the
untracked `local.properties`:

```
sdk.dir=/path/to/Android/Sdk
api.base.url=https://<cloudfront-domain>/v1
```

or export `API_BASE_URL` (CI reads it from the `API_BASE_URL` Actions secret,
which Terraform manages). Then:

```
./gradlew testDebugUnitTest assembleDebug
./gradlew connectedDebugAndroidTest   # EXIF writer test on a device/emulator
```

## How a photo flows

1. Capture with CameraX (JPEG quality 90); a fresh location fix is requested on
   shutter press (5 s timeout, else last known fix under 2 minutes old).
2. GPS, time and device tags are written with ExifInterface before the photo is
   queued, and the file is copied to `Pictures/PhotoDashboard`.
3. A WorkManager job per photo (network required, exponential backoff, up to 10
   attempts) gets a presigned URL, PUTs the file, and registers it. The server
   `photo_id` is stored in Room and reused on retry, so uploads are idempotent.

v1 security is deliberately minimal (token in plain DataStore, public photo
URLs); see the backend spec's v2 plan.
