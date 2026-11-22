package pl.vlsmcalc.nettools

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WolDevice(val name: String, val mac: String)

enum class PingMode(val label: String) { ICMP("ICMP"), TCP("TCP") }

enum class PortPreset(val label: String) {
    COMMON("Popularne (20)"),
    NETWORK("Sprzęt sieciowy"),
    WEB("WWW"),
    WELL_KNOWN("1–1024"),
    SINGLE("Jeden port"),
}

data class NetPrefs(
    val favorites: List<String> = emptyList(),
    val recentHosts: List<String> = emptyList(),
    val wol: List<WolDevice> = emptyList(),
    val pingHost: String = "8.8.8.8",
    val pingMode: PingMode = PingMode.ICMP,
    val pingCount: Int = 5,
    val tcpPort: Int = 443,
    val portsHost: String = "192.168.1.1",
    val portPreset: PortPreset = PortPreset.COMMON,
    val singlePort: Int = 22,
    val dnsHost: String = "cisco.com",
    val generatorNet: String = "192.168.10.0/24",
)

class NetStorage(context: Context) {
    private val prefs = context.getSharedPreferences("nettools", Context.MODE_PRIVATE)

    fun load(): NetPrefs = try {
        prefs.getString(KEY, null)?.let { decode(JSONObject(it)) } ?: NetPrefs()
    } catch (_: Exception) {
        NetPrefs()
    }

    fun save(p: NetPrefs) {
        prefs.edit().putString(KEY, encode(p).toString()).apply()
    }

    private fun encode(p: NetPrefs) = JSONObject().apply {
        put("favorites", JSONArray(p.favorites))
        put("recentHosts", JSONArray(p.recentHosts))
        put("wol", JSONArray().apply { p.wol.forEach { put(JSONObject().put("name", it.name).put("mac", it.mac)) } })
        put("pingHost", p.pingHost)
        put("pingMode", p.pingMode.name)
        put("pingCount", p.pingCount)
        put("tcpPort", p.tcpPort)
        put("portsHost", p.portsHost)
        put("portPreset", p.portPreset.name)
        put("singlePort", p.singlePort)
        put("dnsHost", p.dnsHost)
        put("generatorNet", p.generatorNet)
    }

    private fun strings(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }

    private fun decode(o: JSONObject): NetPrefs {
        val d = NetPrefs()
        val wolArr = o.optJSONArray("wol")
        return NetPrefs(
            favorites = strings(o.optJSONArray("favorites")),
            recentHosts = strings(o.optJSONArray("recentHosts")),
            wol = if (wolArr == null) emptyList() else (0 until wolArr.length()).map {
                val w = wolArr.getJSONObject(it)
                WolDevice(w.getString("name"), w.getString("mac"))
            },
            pingHost = o.optString("pingHost", d.pingHost),
            pingMode = runCatching { PingMode.valueOf(o.getString("pingMode")) }.getOrDefault(d.pingMode),
            pingCount = o.optInt("pingCount", d.pingCount),
            tcpPort = o.optInt("tcpPort", d.tcpPort),
            portsHost = o.optString("portsHost", d.portsHost),
            portPreset = runCatching { PortPreset.valueOf(o.getString("portPreset")) }.getOrDefault(d.portPreset),
            singlePort = o.optInt("singlePort", d.singlePort),
            dnsHost = o.optString("dnsHost", d.dnsHost),
            generatorNet = o.optString("generatorNet", d.generatorNet),
        )
    }

    private companion object {
        const val KEY = "prefs_v1"
    }
}
