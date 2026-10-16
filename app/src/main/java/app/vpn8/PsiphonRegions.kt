package app.vpn8

import java.util.Locale

object PsiphonRegions {
    val codes = listOf(
        "AT", "AU", "BE", "BG", "BR", "CA", "CH", "CZ", "DE", "DK", "EE", "ES", "FI", "FR", "GB", "HR",
        "HU", "IE", "IN", "IT", "JP", "LV", "NL", "NO", "PL", "RO", "RS", "SE", "SG", "SK", "UA", "US",
    )

    fun flag(cc: String): String =
        cc.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")

    fun name(cc: String, lang: String): String = Locale("", cc).getDisplayCountry(Locale(lang)).ifBlank { cc }
}
