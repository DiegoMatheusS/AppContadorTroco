package com.diegomatheus.contadortroco

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth

class TrocoStore(context: Context) {
    private val prefs = context.getSharedPreferences("contador_trocas", Context.MODE_PRIVATE)
    private val key = "database_v1"

    fun load(): TrocoData {
        val saved = prefs.getString(key, null) ?: return TrocoData()
        return decode(saved)
    }

    fun save(data: TrocoData) {
        val content = encode(data)
        check(prefs.edit().putString(key, content).commit()) { "Não foi possível gravar os dados no dispositivo." }
    }

    fun encode(data: TrocoData): String {
        val root = JSONObject().put("version", 1)
        root.put("exchanges", JSONArray().apply { data.exchanges.forEach { e ->
            put(JSONObject().put("id", e.id).put("name", e.name).put("date", e.date)
                .put("notes5", e.notes5).put("notes10", e.notes10)
                .put("enteredTotalCents", e.enteredTotalCents ?: JSONObject.NULL)
                .put("noteHint", e.noteHint))
        } })
        root.put("gifts", JSONArray().apply { data.gifts.forEach { g ->
            put(JSONObject().put("id", g.id).put("name", g.name))
        } })
        root.put("awards", JSONArray().apply { data.awards.forEach { a ->
            put(JSONObject().put("month", a.month).put("personKey", a.personKey)
                .put("name", a.name).put("giftId", a.giftId).put("giftName", a.giftName)
                .put("at", a.at).put("delivered", a.delivered))
        } })
        return root.toString(2)
    }

    fun decode(json: String): TrocoData {
        require(json.length <= 15_000_000) { "Arquivo de backup muito grande." }
        val root = JSONObject(json)
        require(root.getInt("version") == 1) { "Versão de backup não compatível." }
        val exchangeArray = root.getJSONArray("exchanges")
        val giftArray = root.getJSONArray("gifts")
        val awardArray = root.getJSONArray("awards")
        require(exchangeArray.length() <= 100_000 && giftArray.length() <= 1_000 && awardArray.length() <= 100_000) {
            "Backup contém registros demais."
        }
        val exchanges = (0 until exchangeArray.length()).map { index ->
            val obj = exchangeArray.getJSONObject(index)
            Exchange(obj.getString("id"), obj.getString("name"), obj.getString("date"),
                obj.getInt("notes5"), obj.getInt("notes10"),
                if (obj.isNull("enteredTotalCents")) null else obj.getLong("enteredTotalCents"),
                obj.optInt("noteHint", 0)).also {
                require(it.id.isNotBlank() && it.id.length <= 100 &&
                    (if (it.enteredTotalCents != null) TrocoRules.isValidDirectTotal(it.enteredTotalCents, it.noteHint)
                     else TrocoRules.isValidExchange(it.name, it.date, it.notes5, it.notes10))) { "Troca inválida no backup." }
            }
        }
        require(exchanges.distinctBy { it.id }.size == exchanges.size) { "Backup possui trocas duplicadas." }
        val gifts = (0 until giftArray.length()).map { index ->
            val obj = giftArray.getJSONObject(index)
            Gift(obj.getString("id"), obj.getString("name")).also {
                require(it.id.isNotBlank() && it.id.length <= 100 && it.name.isNotBlank() && it.name.length <= 80) {
                    "Presente inválido no backup."
                }
            }
        }
        require(gifts.distinctBy { it.id }.size == gifts.size) { "Presentes duplicados no backup." }
        val awards = (0 until awardArray.length()).map { index ->
            val obj = awardArray.getJSONObject(index)
            Award(obj.getString("month"), obj.getString("personKey"), obj.getString("name"),
                obj.getString("giftId"), obj.getString("giftName"), obj.getLong("at"),
                obj.getBoolean("delivered")).also { award ->
                require(runCatching { YearMonth.parse(award.month) }.isSuccess &&
                    award.personKey.isNotBlank() && award.personKey.length <= 80 &&
                    TrocoRules.isValidName(award.name) && award.giftId.length <= 100 &&
                    award.giftName.isNotBlank() && award.giftName.length <= 80 && award.at > 0L) {
                    "Sorteio inválido no backup."
                }
            }
        }
        require(awards.distinctBy { it.month to it.personKey }.size == awards.size) {
            "Há mais de um sorteio para a mesma pessoa no mês."
        }
        return TrocoData(exchanges, gifts, awards)
    }
}
