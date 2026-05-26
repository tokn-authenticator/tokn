package me.diamondforge.tokn.data.icon

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

object IconPackParser {

    @Throws(IconPackParseException::class)
    fun parse(json: String): IconPack {
        val obj = try { JSONObject(json) } catch (e: JSONException) {
            throw IconPackParseException("pack.json is not valid JSON", e)
        }
        val uuid = obj.optString("uuid").also {
            try { UUID.fromString(it) } catch (e: IllegalArgumentException) {
                throw IconPackParseException("Bad UUID: $it", e)
            }
        }
        val name = obj.optString("name").ifBlank {
            throw IconPackParseException("Pack name missing")
        }
        val version = if (obj.has("version")) obj.optInt("version", 1) else 1
        val iconsArray = obj.optJSONArray("icons")
            ?: throw IconPackParseException("Pack has no icons[] array")

        val icons = (0 until iconsArray.length()).map { i ->
            parseIcon(iconsArray.getJSONObject(i), i)
        }
        return IconPack(uuid, name, version, icons)
    }

    private fun parseIcon(obj: JSONObject, index: Int): IconPackIcon {
        val filename = obj.optString("filename").ifBlank {
            throw IconPackParseException("icons[$index] missing filename")
        }
        val displayName = obj.optString("name").ifBlank {
            filename.substringAfterLast('/').substringBeforeLast('.')
        }
        val category = if (obj.has("category") && !obj.isNull("category")) {
            obj.optString("category").ifBlank { null }
        } else null
        val issuersArray: JSONArray? = obj.optJSONArray("issuer")
        val issuers = if (issuersArray != null) {
            (0 until issuersArray.length()).map { issuersArray.getString(it) }
        } else emptyList()
        return IconPackIcon(filename, displayName, category, issuers)
    }
}

class IconPackParseException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
