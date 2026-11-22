package com.github.avbox.osc.cloud

import com.github.avbox.osc.net.HttpClients
import com.github.avbox.osc.util.security.CloudCredentialVault
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class GuangYaFile(
    @SerializedName("fileId") val fileId: String = "",
    @SerializedName("parentId") val parentId: String = "",
    @SerializedName("fileName") val fileName: String = "",
    @SerializedName("fileSize") val fileSize: Long = 0,
    @SerializedName("resType") val resType: Int = 0,
    @SerializedName("ctime") val createdAt: Long = 0,
    @SerializedName("utime") val updatedAt: Long = 0,
) {
    val isFolder: Boolean get() = resType == 2
}

data class GuangYaAccountState(
    val phoneNumber: String,
    val loggedIn: Boolean,
    val accessTokenExpiresIn: Long = 0,
)

private data class GuangYaCredential(
    val phoneNumber: String,
    val clientId: String,
    val deviceId: String,
    val accessToken: String,
    val refreshToken: String,
)

private data class ApiEnvelope<T>(
    val code: Int = 0,
    val msg: String = "",
    val data: T? = null,
    val error: String = "",
    @SerializedName("error_description") val errorDescription: String = "",
)

private data class CaptchaData(
    @SerializedName("captcha_token") val captchaToken: String = "",
    val url: String = "",
    val error: String = "",
    @SerializedName("error_description") val errorDescription: String = "",
)

private data class VerificationData(
    @SerializedName("verification_id") val verificationId: String = "",
    val error: String = "",
    @SerializedName("error_description") val errorDescription: String = "",
)

private data class VerifyData(
    @SerializedName("verification_token") val verificationToken: String = "",
    val error: String = "",
    @SerializedName("error_description") val errorDescription: String = "",
)

private data class TokenData(
    @SerializedName("access_token") val accessToken: String = "",
    @SerializedName("refresh_token") val refreshToken: String = "",
    @SerializedName("expires_in") val expiresIn: Long = 0,
    val error: String = "",
    @SerializedName("error_description") val errorDescription: String = "",
)

private data class UserData(val sub: String = "")

private data class FileListData(
    val total: Int = 0,
    val list: List<GuangYaFile> = emptyList(),
)

private data class DownloadData(
    @SerializedName("signedURL") val signedUrl: String = "",
    @SerializedName("downloadUrl") val downloadUrl: String = "",
)

object GuangYaPanClient {

    private const val ACCOUNT_BASE = "https://account.guangyapan.com"
    private const val API_BASE = "https://api.guangyapan.com"
    private const val CLIENT_ID = "aMe-8VSlkrbQXpUR"
    private const val CREDENTIAL_ID = "guangyapan"
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()
    private val refreshLock = Any()
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .proxySelector(HttpClients.proxySelector())
            .proxyAuthenticator(HttpClients.proxyAuthenticator())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun isLoggedIn(): Boolean = runCatching { loadCredential() != null }.getOrDefault(false)

    fun accountPhone(): String = runCatching { loadCredential()?.phoneNumber.orEmpty() }.getOrDefault("")

    fun sendSmsCode(phoneNumber: String): String {
        val phone = normalizePhone(phoneNumber)
        require(phone.isNotBlank()) { "请输入手机号" }
        val deviceId = UUID.randomUUID().toString().replace("-", "")
        val captcha = postAccount<CaptchaData>(
            "/v1/shield/captcha/init",
            mapOf(
                "client_id" to CLIENT_ID,
                "action" to "POST:/v1/auth/verification",
                "device_id" to deviceId,
                "meta" to mapOf(
                    "username" to phone,
                    "phone_number" to phone,
                    "VERIFICATION_PHONE" to phone,
                ),
            ),
            deviceId,
        )
        if (captcha.url.isNotBlank()) {
            throw IOException("光鸭要求完成官方人机验证，请先在光鸭官方客户端完成验证后重试")
        }
        if (captcha.error.isNotBlank()) {
            throw IOException(captcha.errorDescription.ifBlank { captcha.error })
        }
        val captchaToken = captcha.captchaToken
        val verification = postAccount<VerificationData>(
            "/v1/auth/verification",
            mapOf(
                "phone_number" to phone,
                "target" to "ANY",
                "client_id" to CLIENT_ID,
            ),
            deviceId,
            captchaToken,
        )
        if (verification.error.isNotBlank()) throw IOException(verification.errorDescription.ifBlank { verification.error })
        if (verification.verificationId.isBlank()) throw IOException("光鸭未返回短信验证会话")
        savePending(phone, deviceId, verification.verificationId, captchaToken)
        return "验证码已发送，请在有效期内完成登录"
    }

    fun login(phoneNumber: String, verificationCode: String) {
        val phone = normalizePhone(phoneNumber)
        val code = verificationCode.trim()
        require(phone.isNotBlank()) { "请输入手机号" }
        require(code.isNotBlank()) { "请输入短信验证码" }
        val pending = loadPending(phone) ?: throw IOException("请先发送短信验证码")
        val verified = postAccount<VerifyData>(
            "/v1/auth/verification/verify",
            mapOf(
                "verification_id" to pending.verificationId,
                "verification_code" to code,
                "client_id" to CLIENT_ID,
            ),
            pending.deviceId,
        )
        if (verified.error.isNotBlank()) throw IOException(verified.errorDescription.ifBlank { verified.error })
        if (verified.verificationToken.isBlank()) throw IOException("短信验证码校验失败")
        val token = postAccount<TokenData>(
            "/v1/auth/signin",
            mapOf(
                "verification_code" to code,
                "verification_token" to verified.verificationToken,
                "username" to phone,
                "client_id" to CLIENT_ID,
            ),
            pending.deviceId,
        )
        if (token.error.isNotBlank()) throw IOException(token.errorDescription.ifBlank { token.error })
        if (token.accessToken.isBlank() || token.refreshToken.isBlank()) {
            throw IOException("光鸭登录成功但未返回完整令牌")
        }
        CloudCredentialVault.write(
            CREDENTIAL_ID,
            gson.toJson(
                GuangYaCredential(
                    phoneNumber = phone,
                    clientId = CLIENT_ID,
                    deviceId = pending.deviceId,
                    accessToken = token.accessToken,
                    refreshToken = token.refreshToken,
                ),
            ),
        )
        CloudCredentialVault.remove("$CREDENTIAL_ID-pending")
    }

    fun checkAccount(): GuangYaAccountState {
        val credential = loadCredential() ?: throw IOException("光鸭账号尚未登录")
        val user = runCatching {
            accountRequest<UserData>("/v1/user/me", credential, method = "GET")
        }.getOrElse {
            refreshCredential()
            val refreshed = loadCredential() ?: throw IOException("光鸭登录状态已失效")
            accountRequest<UserData>("/v1/user/me", refreshed, method = "GET")
        }
        if (user.sub.isBlank()) throw IOException("光鸭账号状态验证失败")
        return GuangYaAccountState(credential.phoneNumber, true)
    }

    fun listFiles(parentId: String = "", page: Int = 0, pageSize: Int = 100): List<GuangYaFile> {
        require(page >= 0)
        require(pageSize in 1..200)
        var credential = loadCredential() ?: throw IOException("光鸭账号尚未登录")
        var response = postApi<FileListData>(
            "/userres/v1/file/get_file_list",
            mapOf(
                "parentId" to parentId,
                "page" to page,
                "pageSize" to pageSize,
                "orderBy" to 3,
                "sortType" to 1,
            ),
            credential,
        )
        if (response.code == 401 || response.code == 403) {
            refreshCredential()
            credential = loadCredential() ?: throw IOException("光鸭登录状态已失效")
            response = postApi(
                "/userres/v1/file/get_file_list",
                mapOf(
                    "parentId" to parentId,
                    "page" to page,
                    "pageSize" to pageSize,
                    "orderBy" to 3,
                    "sortType" to 1,
                ),
                credential,
            )
        }
        if (response.code == 401 || response.code == 403) {
            throw IOException("光鸭登录状态已失效，请重新登录")
        }
        if (response.msg.isNotBlank() && !response.msg.equals("success", ignoreCase = true)) {
            throw IOException(response.msg)
        }
        return response.data?.list.orEmpty()
    }


    internal fun resolveInAppPlaybackUrl(fileId: String): String {
        require(fileId.isNotBlank()) { "文件 ID 不能为空" }
        var credential = loadCredential() ?: throw IOException("光鸭账号尚未登录")
        var response = postApi<DownloadData>(
            "/nd.bizuserres.s/v1/get_res_download_url",
            mapOf("fileId" to fileId),
            credential,
        )
        if (response.code == 401 || response.code == 403) {
            refreshCredential()
            credential = loadCredential() ?: throw IOException("光鸭登录状态已失效")
            response = postApi(
                "/nd.bizuserres.s/v1/get_res_download_url",
                mapOf("fileId" to fileId),
                credential,
            )
        }
        if (response.code == 401 || response.code == 403) {
            throw IOException("光鸭登录状态已失效，请重新登录")
        }
        if (response.msg.isNotBlank() && !response.msg.equals("success", ignoreCase = true)) {
            throw IOException(response.msg)
        }
        val url = response.data?.signedUrl?.takeIf { it.isNotBlank() }
            ?: response.data?.downloadUrl?.takeIf { it.isNotBlank() }
            ?: throw IOException("光鸭未返回可用的文件播放地址")
        val uri = android.net.Uri.parse(url)
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) {
            throw IOException("光鸭返回的文件地址不符合安全要求")
        }
        return url
    }

    fun logout() {
        CloudCredentialVault.remove(CREDENTIAL_ID)
        CloudCredentialVault.remove("$CREDENTIAL_ID-pending")
    }

    private fun refreshCredential() = synchronized(refreshLock) {
        val current = loadCredential() ?: throw IOException("光鸭账号尚未登录")
        val stillValid = runCatching {
            accountRequest<UserData>("/v1/user/me", current, method = "GET").sub.isNotBlank()
        }.getOrDefault(false)
        if (stillValid) return@synchronized
        val refreshed = postAccount<TokenData>(
            "/v1/auth/token",
            mapOf(
                "client_id" to current.clientId,
                "grant_type" to "refresh_token",
                "refresh_token" to current.refreshToken,
            ),
            current.deviceId,
        )
        if (refreshed.error.isNotBlank()) throw IOException(refreshed.errorDescription.ifBlank { refreshed.error })
        if (refreshed.accessToken.isBlank()) throw IOException("光鸭令牌刷新失败")
        CloudCredentialVault.write(
            CREDENTIAL_ID,
            gson.toJson(
                current.copy(
                    accessToken = refreshed.accessToken,
                    refreshToken = refreshed.refreshToken.ifBlank { current.refreshToken },
                ),
            ),
        )
    }

    private inline fun <reified T> postAccount(
        path: String,
        body: Any,
        deviceId: String,
        captchaToken: String = "",
    ): T {
        val request = accountBuilder(path, deviceId)
            .post(gson.toJson(body).toRequestBody(jsonType))
            .apply { if (captchaToken.isNotBlank()) header("X-Captcha-Token", captchaToken) }
            .build()
        return execute(request, T::class.java)
    }

    private inline fun <reified T> accountRequest(path: String, credential: GuangYaCredential, method: String): T {
        val builder = accountBuilder(path, credential.deviceId)
            .header("Authorization", "Bearer ${credential.accessToken}")
        val request = when (method) {
            "GET" -> builder.get().build()
            else -> builder.post(ByteArray(0).toRequestBody(jsonType)).build()
        }
        return execute(request, T::class.java)
    }

    private fun accountBuilder(path: String, deviceId: String): Request.Builder =
        Request.Builder()
            .url(ACCOUNT_BASE + path)
            .header("Accept", "application/json, text/plain, */*")
            .header("Content-Type", "application/json")
            .header("X-Device-Model", "chrome%2F147.0.0.0")
            .header("X-Device-Name", "PC-Chrome")
            .header("X-Device-Sign", "wdi10.$deviceId")
            .header("X-Net-Work-Type", "NONE")
            .header("X-OS-Version", "MacIntel")
            .header("X-Platform-Version", "1")
            .header("X-Protocol-Version", "301")
            .header("X-Provider-Name", "NONE")
            .header("X-SDK-Version", "9.0.2")
            .header("X-Client-Id", CLIENT_ID)
            .header("X-Client-Version", "0.0.1")
            .header("X-Device-Id", deviceId)

    private inline fun <reified T> postApi(path: String, body: Any, credential: GuangYaCredential): ApiEnvelope<T> {
        val request = Request.Builder()
            .url(API_BASE + path)
            .header("Accept", "application/json, text/plain, */*")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${credential.accessToken}")
            .header("Did", credential.deviceId)
            .header("Dt", "4")
            .post(gson.toJson(body).toRequestBody(jsonType))
            .build()
        val type = TypeToken.getParameterized(ApiEnvelope::class.java, T::class.java).type
        return execute(request, type, allowAuthError = true)
    }

    private fun <T> execute(
        request: Request,
        type: java.lang.reflect.Type,
        allowAuthError: Boolean = false,
    ): T {
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val authError = allowAuthError && response.code in 401..403
            if (!response.isSuccessful && !authError) {
                throw IOException("光鸭服务请求失败（HTTP " + response.code + "）")
            }
            val parsed = runCatching { gson.fromJson<T>(text, type) }.getOrNull()
            if (authError) {
                if (parsed is ApiEnvelope<*>) {
                    @Suppress("UNCHECKED_CAST")
                    return (parsed.copy(code = response.code) as T)
                }
                @Suppress("UNCHECKED_CAST")
                return (ApiEnvelope<Any>(code = response.code, msg = "unauthorized") as T)
            }
            val result = parsed ?: throw IOException("光鸭服务返回了无法识别的数据")
            if (result is ApiEnvelope<*> && result.error.isNotBlank()) {
                throw IOException(result.errorDescription.ifBlank { result.error })
            }
            return result
        }
    }

    private fun savePending(phone: String, deviceId: String, verificationId: String, captchaToken: String) {
        CloudCredentialVault.write(
            "$CREDENTIAL_ID-pending",
            gson.toJson(PendingLogin(phone, deviceId, verificationId, captchaToken)),
        )
    }

    private fun loadPending(phone: String): PendingLogin? {
        val raw = CloudCredentialVault.read("$CREDENTIAL_ID-pending") ?: return null
        val pending = gson.fromJson(raw, PendingLogin::class.java) ?: throw IOException("短信登录会话无效，请重新发送验证码")
        return pending.takeIf { it.phoneNumber == phone }
    }

    private fun loadCredential(): GuangYaCredential? {
        val raw = CloudCredentialVault.read(CREDENTIAL_ID) ?: return null
        return gson.fromJson(raw, GuangYaCredential::class.java) ?: throw IOException("保存的光鸭账号信息无效")
    }

    private fun normalizePhone(value: String): String {
        val phone = value.trim().replace(" ", "")
        return if (phone.startsWith("+")) phone
        else if (phone.length == 11 && phone.all(Char::isDigit)) "+86 $phone"
        else phone
    }

    private data class PendingLogin(
        val phoneNumber: String,
        val deviceId: String,
        val verificationId: String,
        val captchaToken: String,
    )
}
