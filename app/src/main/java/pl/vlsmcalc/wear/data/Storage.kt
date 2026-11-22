package pl.vlsmcalc.wear.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import pl.vlsmcalc.wear.core.FlsmMode
import pl.vlsmcalc.wear.core.SubnetRequest

data class SavedProject(
    val name: String,
    val base: String,
    val subnets: List<SubnetRequest>,
    val largestFirst: Boolean,
    val allowSmall: Boolean,
    val savedAt: Long,
)

/** Pełny stan aplikacji zapisywany automatycznie, żeby nic nie ginęło po zamknięciu. */
data class AppState(
    val vlsmBase: String = "192.168.1.0/24",
    val subnets: List<SubnetRequest> = emptyList(),
    val largestFirst: Boolean = true,
    val allowSmall: Boolean = false,
    val ipCalc: String = "192.168.10.77/26",
    val flsmBase: String = "192.168.0.0/24",
    val flsmMode: FlsmMode = FlsmMode.BY_COUNT,
    val flsmValue: Long = 4,
    val summaryNets: List<String> = emptyList(),
    val checkA: String = "192.168.1.10/24",
    val checkB: String = "192.168.1.200",
    val hostsTool: Long = 50,
    val saved: List<SavedProject> = emptyList(),
)

class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("vlsm_state", Context.MODE_PRIVATE)

    fun load(): AppState = try {
        prefs.getString(KEY, null)?.let { decode(JSONObject(it)) } ?: AppState()
    } catch (_: Exception) {
        AppState()
    }

    fun save(state: AppState) {
        prefs.edit().putString(KEY, encode(state).toString()).apply()
    }

    private fun encodeSubnets(list: List<SubnetRequest>) = JSONArray().apply {
        list.forEach { put(JSONObject().put("n", it.name).put("h", it.hosts)) }
    }

    private fun decodeSubnets(arr: JSONArray?): List<SubnetRequest> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            SubnetRequest(o.getString("n"), o.getLong("h"))
        }
    }

    private fun encode(s: AppState) = JSONObject().apply {
        put("vlsmBase", s.vlsmBase)
        put("subnets", encodeSubnets(s.subnets))
        put("largestFirst", s.largestFirst)
        put("allowSmall", s.allowSmall)
        put("ipCalc", s.ipCalc)
        put("flsmBase", s.flsmBase)
        put("flsmMode", s.flsmMode.name)
        put("flsmValue", s.flsmValue)
        put("summaryNets", JSONArray(s.summaryNets))
        put("checkA", s.checkA)
        put("checkB", s.checkB)
        put("hostsTool", s.hostsTool)
        put("saved", JSONArray().apply {
            s.saved.forEach { p ->
                put(
                    JSONObject()
                        .put("name", p.name)
                        .put("base", p.base)
                        .put("subnets", encodeSubnets(p.subnets))
                        .put("largestFirst", p.largestFirst)
                        .put("allowSmall", p.allowSmall)
                        .put("savedAt", p.savedAt)
                )
            }
        })
    }

    private fun decode(o: JSONObject): AppState {
        val d = AppState()
        val savedArr = o.optJSONArray("saved")
        val saved = if (savedArr == null) emptyList() else (0 until savedArr.length()).map {
            val p = savedArr.getJSONObject(it)
            SavedProject(
                name = p.getString("name"),
                base = p.getString("base"),
                subnets = decodeSubnets(p.optJSONArray("subnets")),
                largestFirst = p.optBoolean("largestFirst", true),
                allowSmall = p.optBoolean("allowSmall", false),
                savedAt = p.optLong("savedAt", 0),
            )
        }
        val sumArr = o.optJSONArray("summaryNets")
        return AppState(
            vlsmBase = o.optString("vlsmBase", d.vlsmBase),
            subnets = decodeSubnets(o.optJSONArray("subnets")),
            largestFirst = o.optBoolean("largestFirst", d.largestFirst),
            allowSmall = o.optBoolean("allowSmall", d.allowSmall),
            ipCalc = o.optString("ipCalc", d.ipCalc),
            flsmBase = o.optString("flsmBase", d.flsmBase),
            flsmMode = runCatching { FlsmMode.valueOf(o.optString("flsmMode")) }.getOrDefault(d.flsmMode),
            flsmValue = o.optLong("flsmValue", d.flsmValue),
            summaryNets = if (sumArr == null) emptyList() else (0 until sumArr.length()).map { sumArr.getString(it) },
            checkA = o.optString("checkA", d.checkA),
            checkB = o.optString("checkB", d.checkB),
            hostsTool = o.optLong("hostsTool", d.hostsTool),
            saved = saved,
        )
    }

    private companion object {
        const val KEY = "state_v1"
    }
}
