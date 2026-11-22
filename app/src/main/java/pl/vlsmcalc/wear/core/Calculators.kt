package pl.vlsmcalc.wear.core

data class SubnetRequest(val name: String, val hosts: Long)

data class Allocation(
    /** Pozycja wymagania na liście wprowadzonej przez użytkownika. */
    val requestIndex: Int,
    val request: SubnetRequest,
    val subnet: Cidr,
) {
    val spareHosts: Long get() = subnet.usableHosts - request.hosts
}

data class VlsmResult(
    val base: Cidr,
    /** Przydziały posortowane rosnąco po adresie. */
    val allocations: List<Allocation>,
    /** Wolne bloki pozostałe w sieci bazowej (minimalny zbiór CIDR). */
    val free: List<Cidr>,
) {
    val usedAddresses: Long get() = allocations.sumOf { it.subnet.size }
    val totalAddresses: Long get() = base.size
    val requestedHosts: Long get() = allocations.sumOf { it.request.hosts }
    val usableHosts: Long get() = allocations.sumOf { it.subnet.usableHosts }

    /** Procent wykorzystania przestrzeni sieci bazowej przez przydzielone bloki. */
    val usagePercent: Double get() = usedAddresses * 100.0 / totalAddresses

    /** Efektywność: wymagane hosty / adresy w przydzielonych blokach. */
    val efficiencyPercent: Double get() =
        if (usedAddresses == 0L) 0.0 else requestedHosts * 100.0 / usedAddresses
}

sealed interface VlsmOutcome {
    data class Success(val result: VlsmResult) : VlsmOutcome

    /** Wymagania nie mieszczą się w sieci bazowej. */
    data class DoesNotFit(
        val neededAddresses: Long,
        val availableAddresses: Long,
        /** Najdłuższy prefiks sieci bazowej, przy którym wszystko się zmieści (null = niemożliwe). */
        val suggestedPrefix: Int?,
        /** Nazwa pierwszej podsieci, której nie udało się zmieścić. */
        val failedAt: String,
    ) : VlsmOutcome

    data class Invalid(val message: String) : VlsmOutcome
}

object Vlsm {
    fun calculate(
        base: Cidr,
        requests: List<SubnetRequest>,
        largestFirst: Boolean = true,
        allowSmall: Boolean = false,
    ): VlsmOutcome {
        if (requests.isEmpty()) return VlsmOutcome.Invalid("Dodaj co najmniej jedną podsieć")
        val prefixes = requests.map { r ->
            Ip.prefixForHosts(r.hosts, allowSmall)
                ?: return VlsmOutcome.Invalid("Nieprawidłowa liczba hostów: ${r.name}")
        }
        val order = requests.indices.let { idx ->
            if (largestFirst) idx.sortedWith(compareBy<Int> { prefixes[it] }.thenBy { it }) else idx.toList()
        }
        val attempt = allocate(base.normalized(), order, requests, prefixes)
        if (attempt is AllocAttempt.Ok) {
            val sorted = attempt.allocations.sortedBy { it.subnet.network }
            return VlsmOutcome.Success(VlsmResult(base.normalized(), sorted, freeBlocks(base.normalized(), sorted.map { it.subnet })))
        }
        val failed = (attempt as AllocAttempt.Failed).failedIndex
        val needed = order.sumOf { Ip.blockSize(prefixes[it]) }
        var suggestion: Int? = null
        for (p in base.prefix - 1 downTo 0) {
            if (allocate(Cidr(base.network, p).normalized(), order, requests, prefixes) is AllocAttempt.Ok) {
                suggestion = p
                break
            }
        }
        return VlsmOutcome.DoesNotFit(needed, base.size, suggestion, requests[failed].name)
    }

    private sealed interface AllocAttempt {
        data class Ok(val allocations: List<Allocation>) : AllocAttempt
        data class Failed(val failedIndex: Int) : AllocAttempt
    }

    private fun allocate(
        base: Cidr,
        order: List<Int>,
        requests: List<SubnetRequest>,
        prefixes: List<Int>,
    ): AllocAttempt {
        var cursor = base.network
        val result = ArrayList<Allocation>(order.size)
        for (i in order) {
            val size = Ip.blockSize(prefixes[i])
            // Wyrównanie początku bloku do jego rozmiaru (istotne, gdy kolejność nie jest malejąca).
            val start = ((cursor + size - 1) / size) * size
            val end = start + size - 1
            if (end > base.broadcast || prefixes[i] < base.prefix) return AllocAttempt.Failed(i)
            result += Allocation(i, requests[i], Cidr(start, prefixes[i]))
            cursor = end + 1
        }
        return AllocAttempt.Ok(result)
    }

    /** Wolne fragmenty [base] po odjęciu [used] (bloki nie mogą na siebie zachodzić). */
    fun freeBlocks(base: Cidr, used: List<Cidr>): List<Cidr> {
        val sorted = used.sortedBy { it.network }
        val free = ArrayList<Cidr>()
        var cursor = base.network
        for (block in sorted) {
            if (block.network > cursor) free += Ranges.toCidrs(cursor, block.network - 1)
            cursor = maxOf(cursor, block.broadcast + 1)
        }
        if (cursor <= base.broadcast) free += Ranges.toCidrs(cursor, base.broadcast)
        return free
    }
}

object Ranges {
    /** Rozkłada zakres [start, end] na minimalną listę bloków CIDR. */
    fun toCidrs(start: Long, end: Long): List<Cidr> {
        val out = ArrayList<Cidr>()
        var cur = start
        while (cur <= end) {
            var size = if (cur == 0L) 1L shl 32 else java.lang.Long.lowestOneBit(cur)
            while (cur + size - 1 > end) size = size shr 1
            val prefix = 32 - java.lang.Long.numberOfTrailingZeros(size)
            out += Cidr(cur, prefix)
            cur += size
        }
        return out
    }
}

data class FlsmPlan(val base: Cidr, val newPrefix: Int) {
    val count: Long get() = 1L shl (newPrefix - base.prefix)
    val subnetSize: Long get() = Ip.blockSize(newPrefix)
    fun subnet(index: Long): Cidr = Cidr(base.network + index * subnetSize, newPrefix)
}

enum class FlsmMode(val label: String, val valueLabel: String) {
    BY_COUNT("Liczba podsieci", "Podsieci"),
    BY_HOSTS("Hosty w podsieci", "Hosty"),
    BY_PREFIX("Nowy prefiks", "Prefiks"),
}

object Flsm {
    sealed interface Outcome {
        data class Ok(val plan: FlsmPlan) : Outcome
        data class Error(val message: String) : Outcome
    }

    fun plan(base: Cidr, mode: FlsmMode, value: Long, allowSmall: Boolean = false): Outcome {
        val b = base.normalized()
        val newPrefix = when (mode) {
            FlsmMode.BY_COUNT -> {
                if (value < 1) return Outcome.Error("Podaj liczbę ≥ 1")
                var bits = 0
                while ((1L shl bits) < value) bits++
                b.prefix + bits
            }
            FlsmMode.BY_HOSTS -> Ip.prefixForHosts(value, allowSmall) ?: return Outcome.Error("Podaj liczbę ≥ 1")
            FlsmMode.BY_PREFIX -> value.toInt().also { if (value !in 0..32) return Outcome.Error("Prefiks 0–32") }
        }
        if (newPrefix > 32) return Outcome.Error("Za dużo podsieci dla /${b.prefix}")
        if (newPrefix < b.prefix) return Outcome.Error("Nie mieści się w /${b.prefix}")
        return Outcome.Ok(FlsmPlan(b, newPrefix))
    }
}

data class SummaryResult(
    val summary: Cidr,
    /** Minimalny zbiór CIDR dokładnie pokrywający wejście. */
    val aggregated: List<Cidr>,
    /** Adresy objęte trasą sumaryczną, których nie ma w żadnej sieci wejściowej. */
    val extraAddresses: Long,
)

object Summarizer {
    fun summarize(networks: List<Cidr>): SummaryResult? {
        if (networks.isEmpty()) return null
        val lo = networks.minOf { it.network }
        val hi = networks.maxOf { it.broadcast }
        val diff = lo xor hi
        val commonBits = if (diff == 0L) 32 else java.lang.Long.numberOfLeadingZeros(diff) - 32
        val summary = Cidr(lo, commonBits).normalized()
        val aggregated = aggregate(networks)
        val covered = aggregated.sumOf { it.size }
        return SummaryResult(summary, aggregated, summary.size - covered)
    }

    fun aggregate(networks: List<Cidr>): List<Cidr> {
        val ranges = networks.map { it.network to it.broadcast }.sortedBy { it.first }
        val merged = ArrayList<Pair<Long, Long>>()
        for (r in ranges) {
            val last = merged.lastOrNull()
            if (last != null && r.first <= last.second + 1) {
                merged[merged.lastIndex] = last.first to maxOf(last.second, r.second)
            } else {
                merged += r
            }
        }
        return merged.flatMap { Ranges.toCidrs(it.first, it.second) }
    }
}
