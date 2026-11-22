package pl.vlsmcalc.nettools.ios

import java.text.Normalizer

/**
 * Wyszukiwanie poleceń po składni, opisie i kategorii.
 * Rozumie skróty IOS: „sh ip int br” znajdzie „show ip interface brief”, „conf t” – „configure terminal”.
 */
object IosSearch {
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase().replace('ł', 'l'), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    private fun words(text: String): List<String> =
        normalize(text).split(Regex("[^a-z0-9.:/-]+")).filter { it.isNotEmpty() }

    /** Czy kolejne słowa zapytania są prefiksami kolejnych słów składni (jak skróty w CLI). */
    fun matchesAbbreviation(query: String, syntax: String): Boolean {
        val q = words(query)
        if (q.isEmpty()) return false
        val s = words(syntax)
        var i = 0
        for (word in s) {
            if (i < q.size && word.startsWith(q[i])) i++
        }
        return i == q.size && s.isNotEmpty() && s[0].startsWith(q[0])
    }

    fun score(cmd: IosCommand, query: String, categoryName: String = ""): Int {
        val q = normalize(query.trim())
        if (q.isEmpty()) return 0
        val syntax = normalize(cmd.syntax)
        val title = normalize(cmd.title)
        if (syntax.startsWith(q)) return 100
        if (matchesAbbreviation(q, cmd.syntax)) return 90
        if (syntax.contains(q)) return 70
        if (title.contains(q)) return 60
        val haystack = listOf(syntax, title, normalize(cmd.description), normalize(cmd.example ?: ""),
            normalize(cmd.tip ?: ""), normalize(categoryName)).joinToString(" ")
        val tokens = words(q)
        if (tokens.isNotEmpty() && tokens.all { haystack.contains(it) }) return 30
        return 0
    }

    fun search(query: String, limit: Int = 60): List<IosCommand> {
        val names = IosDatabase.categories.associate { it.id to it.name }
        return IosDatabase.commands
            .map { it to score(it, query, names[it.category].orEmpty()) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
}
