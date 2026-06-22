package com.hereliesaz.logkitty.utils

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * GitHub OAuth **device flow** — an alternative to pasting a PAT. The user gets a short code to enter
 * at github.com/login/device; we poll until they authorize, then hand back an access token that the
 * caller stores in [GitHubCredentials] (same secure store the PAT uses).
 *
 * Needs a registered GitHub OAuth app **client id** (BuildConfig.GITHUB_OAUTH_CLIENT_ID); when that's
 * blank the UI hides the option and only PAT entry is offered. No client secret is involved (device
 * flow is for public clients). All calls are suspend + run on [Dispatchers.IO].
 */
object GitHubDeviceAuth {

    /** The user-facing code + where to enter it, plus the polling parameters. */
    data class DeviceCode(
        val userCode: String,
        val verificationUri: String,
        val deviceCode: String,
        val intervalSec: Int,
        val expiresInSec: Int,
    )

    /** Step 1: ask GitHub for a device/user code pair. Returns null on failure. */
    suspend fun requestDeviceCode(clientId: String, scope: String = "repo"): DeviceCode? =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder().add("client_id", clientId).add("scope", scope).build()
            val req = Request.Builder()
                .url("https://github.com/login/device/code")
                .header("Accept", "application/json")
                .post(body)
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val o = JSONObject(resp.body?.string().orEmpty())
                    val userCode = o.optString("user_code")
                    val deviceCode = o.optString("device_code")
                    if (userCode.isBlank() || deviceCode.isBlank()) return@withContext null
                    DeviceCode(
                        userCode = userCode,
                        verificationUri = o.optString("verification_uri").ifBlank { "https://github.com/login/device" },
                        deviceCode = deviceCode,
                        intervalSec = o.optInt("interval", 5).coerceAtLeast(1),
                        expiresInSec = o.optInt("expires_in", 900).coerceAtLeast(30),
                    )
                }
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Step 2: poll for the access token until the user authorizes (or it times out / is denied).
     * Honors `authorization_pending` (keep waiting) and `slow_down` (lengthen the interval). Returns
     * the token, or null on denial/expiry/error.
     */
    suspend fun pollForToken(clientId: String, code: DeviceCode): String? = withContext(Dispatchers.IO) {
        var interval = code.intervalSec
        var waited = 0
        while (waited < code.expiresInSec) {
            delay(interval * 1000L)
            waited += interval
            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("device_code", code.deviceCode)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .build()
            val req = Request.Builder()
                .url("https://github.com/login/oauth/access_token")
                .header("Accept", "application/json")
                .post(body)
                .build()
            val o = try {
                client.newCall(req).execute().use { resp -> JSONObject(resp.body?.string().orEmpty()) }
            } catch (e: Exception) {
                continue // transient — try again next interval
            }
            val token = o.optString("access_token")
            if (token.isNotBlank()) return@withContext token
            when (o.optString("error")) {
                "authorization_pending" -> { /* keep waiting */ }
                "slow_down" -> interval += 5
                else -> return@withContext null // expired_token / access_denied / unsupported
            }
        }
        null
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}
