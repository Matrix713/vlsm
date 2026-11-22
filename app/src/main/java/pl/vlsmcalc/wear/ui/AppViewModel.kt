package pl.vlsmcalc.wear.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import pl.vlsmcalc.wear.core.Cidr
import pl.vlsmcalc.wear.core.Flsm
import pl.vlsmcalc.wear.core.FlsmMode
import pl.vlsmcalc.wear.core.Ip
import pl.vlsmcalc.wear.core.SubnetRequest
import pl.vlsmcalc.wear.core.Summarizer
import pl.vlsmcalc.wear.core.SummaryResult
import pl.vlsmcalc.wear.core.Vlsm
import pl.vlsmcalc.wear.core.VlsmOutcome
import pl.vlsmcalc.wear.data.AppState
import pl.vlsmcalc.wear.data.SavedProject
import pl.vlsmcalc.wear.data.Storage

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = Storage(app)

    var state by mutableStateOf(storage.load())
        private set

    /** Bieżące żądanie klawiatury (ekran "keypad"). */
    var input: InputRequest? by mutableStateOf(null)
        private set

    private fun update(block: AppState.() -> AppState) {
        state = state.block()
        storage.save(state)
    }

    fun openInput(request: InputRequest) {
        input = request
    }

    // ---------- VLSM ----------

    fun vlsmBaseCidr(): Cidr? = Cidr.parse(state.vlsmBase)

    fun computeVlsm(): VlsmOutcome {
        val base = vlsmBaseCidr() ?: return VlsmOutcome.Invalid("Błędna sieć bazowa")
        return Vlsm.calculate(base, state.subnets, state.largestFirst, state.allowSmall)
    }

    fun setVlsmBase(text: String) = update { copy(vlsmBase = text) }

    fun setVlsmBasePrefix(prefix: Int) {
        val base = vlsmBaseCidr() ?: return
        update { copy(vlsmBase = Cidr(base.network, prefix).normalized().toString()) }
    }

    fun setLargestFirst(v: Boolean) = update { copy(largestFirst = v) }
    fun setAllowSmall(v: Boolean) = update { copy(allowSmall = v) }

    fun nextSubnetName(): String {
        val used = state.subnets.map { it.name }.toSet()
        for (c in 'A'..'Z') if ("LAN $c" !in used) return "LAN $c"
        var i = 27
        while ("LAN $i" in used) i++
        return "LAN $i"
    }

    /** Dodaje podsieć i zwraca jej indeks. */
    fun addSubnet(hosts: Long = 10): Int {
        update { copy(subnets = subnets + SubnetRequest(nextSubnetName(), hosts)) }
        return state.subnets.lastIndex
    }

    fun updateSubnet(index: Int, block: SubnetRequest.() -> SubnetRequest) {
        if (index !in state.subnets.indices) return
        update { copy(subnets = subnets.toMutableList().also { it[index] = it[index].block() }) }
    }

    fun removeSubnet(index: Int) {
        if (index !in state.subnets.indices) return
        update { copy(subnets = subnets.toMutableList().also { it.removeAt(index) }) }
    }

    fun clearVlsm() = update { copy(subnets = emptyList()) }

    fun loadVlsmExample() = update {
        copy(
            vlsmBase = "192.168.1.0/24",
            subnets = listOf(
                SubnetRequest("Biuro", 60),
                SubnetRequest("Magazyn", 28),
                SubnetRequest("WiFi", 25),
                SubnetRequest("Serwery", 12),
                SubnetRequest("WAN 1", 2),
                SubnetRequest("WAN 2", 2),
            ),
        )
    }

    fun saveCurrentProject(): String {
        val n = state.subnets.size
        val name = "${state.vlsmBase} · $n ${plural(n.toLong(), "sieć", "sieci", "sieci")}"
        val project = SavedProject(
            name, state.vlsmBase, state.subnets, state.largestFirst, state.allowSmall, System.currentTimeMillis(),
        )
        update { copy(saved = listOf(project) + saved) }
        return name
    }

    fun loadProject(index: Int) {
        val p = state.saved.getOrNull(index) ?: return
        update { copy(vlsmBase = p.base, subnets = p.subnets, largestFirst = p.largestFirst, allowSmall = p.allowSmall) }
    }

    fun renameProject(index: Int, name: String) {
        if (index !in state.saved.indices || name.isBlank()) return
        update { copy(saved = saved.toMutableList().also { it[index] = it[index].copy(name = name.trim()) }) }
    }

    fun deleteProject(index: Int) {
        if (index !in state.saved.indices) return
        update { copy(saved = saved.toMutableList().also { it.removeAt(index) }) }
    }

    // ---------- Kalkulator IP ----------

    fun setIpCalc(text: String) = update { copy(ipCalc = text) }

    fun adjustIpCalcPrefix(delta: Int) {
        val c = Cidr.parse(state.ipCalc) ?: return
        update { copy(ipCalc = Cidr(c.address, (c.prefix + delta).coerceIn(0, 32)).toString()) }
    }

    fun setIpCalcPrefix(prefix: Int) {
        val c = Cidr.parse(state.ipCalc) ?: return
        update { copy(ipCalc = Cidr(c.address, prefix).toString()) }
    }

    // ---------- FLSM ----------

    fun setFlsmBase(text: String) = update { copy(flsmBase = text) }
    fun setFlsmValue(v: Long) = update { copy(flsmValue = v) }

    fun cycleFlsmMode() = update {
        val modes = FlsmMode.entries
        val next = modes[(flsmMode.ordinal + 1) % modes.size]
        val base = Cidr.parse(flsmBase)
        val value = when (next) {
            FlsmMode.BY_COUNT -> 4L
            FlsmMode.BY_HOSTS -> 30L
            FlsmMode.BY_PREFIX -> ((base?.prefix ?: 24) + 2).coerceAtMost(32).toLong()
        }
        copy(flsmMode = next, flsmValue = value)
    }

    fun computeFlsm(): Flsm.Outcome {
        val base = Cidr.parse(state.flsmBase) ?: return Flsm.Outcome.Error("Błędna sieć")
        return Flsm.plan(base, state.flsmMode, state.flsmValue)
    }

    // ---------- Sumaryzacja ----------

    fun computeSummary(): SummaryResult? = Summarizer.summarize(state.summaryNets.mapNotNull { Cidr.parse(it) })

    fun setSummaryNet(index: Int, text: String?) = update {
        val list = summaryNets.toMutableList()
        when {
            text.isNullOrBlank() -> if (index in list.indices) list.removeAt(index)
            index in list.indices -> list[index] = text
            else -> list += text
        }
        copy(summaryNets = list)
    }

    fun clearSummary() = update { copy(summaryNets = emptyList()) }

    fun loadSummaryExample() = update {
        copy(summaryNets = listOf("172.16.0.0/24", "172.16.1.0/24", "172.16.2.0/24", "172.16.3.0/24"))
    }

    // ---------- Sprawdzanie ----------

    fun setCheckA(text: String) = update { copy(checkA = text) }
    fun setCheckB(text: String) = update { copy(checkB = text) }

    fun adjustCheckPrefix(delta: Int) {
        val c = Cidr.parse(state.checkA) ?: return
        update { copy(checkA = Cidr(c.address, (c.prefix + delta).coerceIn(0, 32)).toString()) }
    }

    // ---------- Ile hostów ----------

    fun setHostsTool(v: Long) = update { copy(hostsTool = v.coerceIn(1, MAX_HOSTS)) }

    companion object {
        /** Maksymalna liczba hostów mieszcząca się w IPv4 (/0 bez sieci i broadcastu). */
        const val MAX_HOSTS: Long = Ip.MAX - 1
    }
}

