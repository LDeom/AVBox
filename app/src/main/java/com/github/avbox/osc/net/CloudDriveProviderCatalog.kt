package com.github.avbox.osc.net

import com.github.avbox.osc.R

data class CloudDriveProvider(
    val id: String,
    val title: Int,
    val loginUrl: String,
    val cookieHosts: Set<String>,
    val loginCookieNames: Set<String>,
)

object CloudDriveProviderCatalog {
    val providers = listOf(
        CloudDriveProvider("quark", R.string.cloud_drive_quark, "https://pan.quark.cn/", setOf("pan.quark.cn", "drive.quark.cn", "api.quark.cn"), setOf("cookie_token", "__uid")),
        CloudDriveProvider("uc", R.string.cloud_drive_uc, "https://drive.uc.cn/", setOf("drive.uc.cn", "pc-api.uc.cn", "api.open.uc.cn"), setOf("_U", "__uid")),
        CloudDriveProvider("baidu", R.string.cloud_drive_baidu, "https://pan.baidu.com/", setOf("pan.baidu.com", "yun.baidu.com", "d.pcs.baidu.com"), setOf("BDUSS")),
        CloudDriveProvider("xunlei", R.string.cloud_drive_xunlei, "https://pan.xunlei.com/", setOf("pan.xunlei.com", "api-pan.xunlei.com", "xluser-ssl.xunlei.com"), setOf("sessionid", "userid")),
        CloudDriveProvider("aliyun", R.string.cloud_drive_aliyun, "https://www.alipan.com/", setOf("www.alipan.com", "openapi.alipan.com", "api.aliyundrive.com", "auth.alipan.com"), setOf("token", "login_aliyunid")),
        CloudDriveProvider("guangya", R.string.cloud_drive_guangya, "https://account.guangyapan.com/", setOf("api.guangyapan.com", "account.guangyapan.com"), setOf("session", "token")),
        CloudDriveProvider("tianyi", R.string.cloud_drive_tianyi, "https://cloud.189.cn/", setOf("cloud.189.cn", "api.cloud.189.cn", "web.cloud.189.cn"), setOf("COOKIE_LOGIN_USER", "SESSION")),
        CloudDriveProvider("pan123", R.string.cloud_drive_123, "https://www.123pan.com/", setOf("www.123pan.com", "api.123pan.com", "open-api.123pan.com", "yun.123pan.cn"), setOf("uid", "auth_key")),
    )

    fun find(id: String): CloudDriveProvider? = providers.firstOrNull { it.id == id }

    fun hasLoginCookie(providerId: String, cookies: Map<String, String>): Boolean {
        val provider = find(providerId) ?: return false
        return provider.loginCookieNames.any { name -> cookies[name]?.isNotBlank() == true }
    }
}
