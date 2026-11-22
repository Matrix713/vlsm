package pl.vlsmcalc.wear.ui

enum class InputMode { IP_CIDR, IP, NUMBER }

/** Żądanie wprowadzenia wartości z klawiatury. [apply] zwraca komunikat błędu albo null. */
class InputRequest(
    val title: String,
    val mode: InputMode,
    val initial: String,
    val allowEmpty: Boolean = false,
    val preview: (String) -> String? = { null },
    val apply: (String) -> String?,
)
