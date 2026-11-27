package pl.vlsmcalc.ccna.study

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Zapis postępów nauki w SharedPreferences (JSON). */
class StudyStore(context: Context) {
    private val prefs = context.getSharedPreferences("ccna_progress", Context.MODE_PRIVATE)

    fun load(): Progress = try {
        prefs.getString(KEY, null)?.let { decode(JSONObject(it)) } ?: Progress()
    } catch (_: Exception) {
        Progress()
    }

    fun save(p: Progress) {
        prefs.edit().putString(KEY, encode(p).toString()).apply()
    }

    private fun encode(p: Progress) = JSONObject().apply {
        put("read", JSONArray(p.read.toList()))
        put("boxes", JSONObject().apply { p.boxes.forEach { (k, v) -> put(k, v) } })
        put("stats", JSONObject().apply {
            p.stats.forEach { (k, s) -> put(k, JSONArray().put(s.attempts).put(s.correct).put(s.lastCorrect)) }
        })
        put("exams", JSONArray().apply {
            p.exams.forEach { put(JSONArray().put(it.at).put(it.total).put(it.correct)) }
        })
    }

    private fun decode(o: JSONObject): Progress {
        val read = o.optJSONArray("read")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
        val boxes = o.optJSONObject("boxes")?.let { b -> b.keys().asSequence().associateWith { b.getInt(it) } } ?: emptyMap()
        val stats = o.optJSONObject("stats")?.let { s ->
            s.keys().asSequence().associateWith { k ->
                val a = s.getJSONArray(k)
                QStat(a.getInt(0), a.getInt(1), a.getBoolean(2))
            }
        } ?: emptyMap()
        val exams = o.optJSONArray("exams")?.let { a ->
            (0 until a.length()).map { i ->
                val e = a.getJSONArray(i)
                ExamRecord(e.getLong(0), e.getInt(1), e.getInt(2))
            }
        } ?: emptyList()
        return Progress(read, boxes, stats, exams)
    }

    private companion object {
        const val KEY = "progress_v1"
    }
}
