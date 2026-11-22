package com.github.avbox.osc.net

import com.github.avbox.osc.R

data class CloudDriveProvider(
    val id: String,
    val title: Int,
    val loginUrl: String,
    val cookieHosts: Set<String>,
)

object CloudDriveProviderCatalog {
    val providers = listOf(
        CloudDriveProvider("quark", R.string.cloud_drive_quark, "https://pan.quark.cn/", setOf("pan.quark.cn", "drive.quark.cn", "api.quark.cn")),
        CloudDriveProvider("uc", R.string.cloud_drive_uc, "https://drive.uc.cn/", setOf("drive.uc.cn", "pc-api.uc.cn", "api.open.uc.cn")),
        CloudDriveProvider("baidu", R.string.cloud_drive_baidu, "https://pan.baidu.com/", setOf("pan.baidu.com", "yun.baidu.com", "d.pcs.baidu.com")),
        CloudDriveProvider("xunlei", R.string.cloud_drive_xunlei, "https://pan.xunlei.com/", setOf("pan.xunlei.com", "api-pan.xunlei.com", "xluser-ssl.xunlei.com")),
        CloudDriveProvider("aliyun", R.string.cloud_drive_aliyun, "https://www.alipan.com/", setOf("www.alipan.com", "openapi.alipan.com", "api.aliyundrive.com", "auth.alipan.com")),
        CloudDriveProvider("guangya", R.string.cloud_drive_guangya, "https://account.guangyapan.com/", setOf("api.guangyapan.com", "account.guangyapan.com")),
        CloudDriveProvider("tianyi", R.string.cloud_drive_tianyi, "https://cloud.189.cn/", setOf("cloud.189.cn", "api.cloud.189.cn", "web.cloud.189.cn")),
        CloudDriveProvider("pan123", R.string.cloud_drive_123, "https://www.123pan.com/", setOf("www.123pan.com", "api.123pan.com", "open-api.123pan.com", "yun.123pan.cn")),
    )

    fun find(id: String): CloudDriveProvider? = providers.firstOrNull { it.id == id }
}
