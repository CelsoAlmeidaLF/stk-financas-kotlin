/* Ponte JSON ⇄ Map: o registro decifrado vira Map<String, Any?> para a Validacao, e volta a JSON para selar. */
package br.com.systekna.financas.dominio

import kotlinx.serialization.json.*

object JsonMapa {
    fun paraKotlin(e: JsonElement): Any? = when (e) {
        is JsonNull -> null
        is JsonObject -> e.mapValuesTo(LinkedHashMap()) { paraKotlin(it.value) }
        is JsonArray -> e.map { paraKotlin(it) }
        is JsonPrimitive -> when {
            e.isString -> e.content
            e.booleanOrNull != null -> e.boolean
            e.longOrNull != null -> e.long
            else -> e.double
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun mapa(e: JsonElement): Map<String, Any?> = paraKotlin(e) as? Map<String, Any?> ?: throw IllegalArgumentException("Registro inválido.")

    fun paraJson(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is JsonElement -> v
        is String -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is Int -> JsonPrimitive(v)
        is Long -> JsonPrimitive(v)
        is Double -> JsonPrimitive(v)
        is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to paraJson(x) })
        is List<*> -> JsonArray(v.map { paraJson(it) })
        else -> throw IllegalArgumentException("Tipo sem JSON: ${v::class.simpleName}")
    }
}
