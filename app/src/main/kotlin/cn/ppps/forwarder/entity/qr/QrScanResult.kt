package cn.ppps.forwarder.entity.qr

import cn.ppps.forwarder.database.entity.Sender
import cn.ppps.forwarder.utils.TYPE_EMAIL
import cn.ppps.forwarder.utils.TYPE_SMS
import cn.ppps.forwarder.utils.TYPE_SOCKET
import cn.ppps.forwarder.utils.TYPE_TELEGRAM
import cn.ppps.forwarder.utils.TYPE_URL_SCHEME
import cn.ppps.forwarder.utils.TYPE_WEBHOOK
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

data class IncomingRuleDto(
    val from: String?,
    val type: String,
    val token: String,
    val number: String,
    val url: String
)

private fun buildJsonSetting(
    dto: IncomingRuleDto,
    gson: Gson
): String {

    val webParams = mapOf(
        "from" to "{{PACKAGE_NAME}}",
        "text" to "{{MSG}}",
        "iso" to "{{RECEIVE_TIME}}",
        "token" to dto.token,
        "number" to dto.number
    )

    val jsonSettingObject = mapOf(
        "headers" to emptyMap<String, String>(),
        "method" to "POST",
        "proxyAuthenticator" to false,
        "proxyHost" to "",
        "proxyPassword" to "",
        "proxyPort" to "",
        "proxyType" to "DIRECT",
        "proxyUsername" to "",
        "response" to "",
        "secret" to "",
        "webParams" to gson.toJson(webParams),
        "webServer" to dto.url
    )

    return gson.toJson(jsonSettingObject)
}

fun jsonToSenders(
    json: String,
    gson: Gson = Gson()
): List<Sender> {

    val listType = object : TypeToken<List<IncomingRuleDto>>() {}.type
    val incomingList: List<IncomingRuleDto> = gson.fromJson(json, listType)

    val now = Date()

    return incomingList.map { dto ->
        Sender(
            id = 0L, // Room autoGenerate
            type = mapSenderType(dto.type),
            name = "main",
            jsonSetting = buildJsonSetting(dto, gson),
            status = 1,
            time = now
        )
    }
}

private fun mapSenderType(type: String): Int =
    when (type.lowercase()) {
        "push"     -> TYPE_WEBHOOK     // push → webhook
        "sms"      -> TYPE_SMS
        "webhook"  -> TYPE_WEBHOOK
        "telegram" -> TYPE_TELEGRAM
        "email"    -> TYPE_EMAIL
        "socket"   -> TYPE_SOCKET
        "url"      -> TYPE_URL_SCHEME
        else       -> TYPE_WEBHOOK     // безопасный default
    }