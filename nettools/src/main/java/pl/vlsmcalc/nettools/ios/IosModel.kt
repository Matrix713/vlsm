package pl.vlsmcalc.nettools.ios

/** Tryb CLI, w którym wpisuje się polecenie. */
enum class Mode(val label: String, val prompt: String) {
    USER("Użytkownik", "Router>"),
    EXEC("Uprzywilejowany", "Router#"),
    CONF("Konfiguracja globalna", "Router(config)#"),
    IF("Interfejs", "(config-if)#"),
    SUBIF("Podinterfejs", "(config-subif)#"),
    LINE("Linia (con/vty)", "(config-line)#"),
    ROUTER("Proces routingu", "(config-router)#"),
    VLAN("VLAN", "(config-vlan)#"),
    DHCP("Pula DHCP", "(dhcp-config)#"),
    STD_ACL("ACL standardowa", "(config-std-nacl)#"),
    EXT_ACL("ACL rozszerzona", "(config-ext-nacl)#"),
    ROMMON("ROMMON", "rommon 1 >"),
    ANY("Dowolny", ""),
}

data class Category(val id: String, val name: String, val description: String)

data class IosCommand(
    val category: String,
    /** Krótki opis po polsku, np. „Trasa domyślna”. */
    val title: String,
    /** Składnia z parametrami w <nawiasach>. Dla gotowych konfiguracji – wiele linii. */
    val syntax: String,
    val mode: Mode,
    val description: String,
    val example: String? = null,
    val tip: String? = null,
) {
    /** Stabilny identyfikator (do ulubionych). */
    val id: String get() = "$category|$syntax"
    val isTemplate: Boolean get() = syntax.contains('\n')
}
