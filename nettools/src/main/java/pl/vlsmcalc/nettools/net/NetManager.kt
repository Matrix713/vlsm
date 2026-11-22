package pl.vlsmcalc.nettools.net

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import pl.vlsmcalc.wear.core.Cidr
import pl.vlsmcalc.wear.core.Ip
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import kotlin.coroutines.resume

data class NetInfo(
    val transport: String,
    val interfaceName: String?,
    val ipv4: List<Cidr>,
    val ipv6: List<String>,
    val gateway: String?,
    val dns: List<String>,
    val ssid: String?,
    val rssi: Int?,
    val linkSpeedMbps: Int?,
    val frequencyMhz: Int?,
    val validated: Boolean,
    val metered: Boolean,
    val downKbps: Int,
    val upKbps: Int,
) {
    val isWifi: Boolean get() = transport == "Wi-Fi"
}

/**
 * Wybór sieci na Wear OS. Zegarek połączony z telefonem domyślnie używa Bluetooth (proxy przez telefon),
 * więc do narzędzi LAN prosimy system o Wi-Fi i przypinamy do niego cały proces aplikacji.
 */
class NetManager(context: Context) {
    private val app = context.applicationContext
    private val cm = app.getSystemService(ConnectivityManager::class.java)
    private var wifiCallback: ConnectivityManager.NetworkCallback? = null

    var wifiNetwork: Network? = null
        private set

    /** Prosi o sieć Wi-Fi (włącza radio, jeśli Wi-Fi jest dozwolone w ustawieniach). */
    suspend fun acquireWifi(timeoutMs: Long = 12_000): Network? {
        wifiNetwork?.let { if (cm.getNetworkCapabilities(it) != null) return it }
        releaseWifi()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val network = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (cont.isActive) cont.resume(network)
                    }

                    override fun onLost(network: Network) {
                        if (network == wifiNetwork) {
                            wifiNetwork = null
                            cm.bindProcessToNetwork(null)
                        }
                    }

                    override fun onUnavailable() {
                        if (cont.isActive) cont.resume(null)
                    }
                }
                wifiCallback = callback
                try {
                    cm.requestNetwork(request, callback)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        if (network == null) {
            releaseWifi()
            return null
        }
        wifiNetwork = network
        cm.bindProcessToNetwork(network)
        return network
    }

    fun releaseWifi() {
        wifiCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        wifiCallback = null
        wifiNetwork = null
        cm.bindProcessToNetwork(null)
    }

    /** Sieć używana przez narzędzia: przypięte Wi-Fi albo domyślna. */
    fun currentNetwork(): Network? = wifiNetwork ?: cm.activeNetwork

    fun info(network: Network? = currentNetwork()): NetInfo? {
        network ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        val lp: LinkProperties? = cm.getLinkProperties(network)
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "LTE"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth (telefon)"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Inna"
        }
        val v4 = lp?.linkAddresses.orEmpty()
            .filter { it.address is Inet4Address }
            .mapNotNull { la -> Ip.parse(la.address.hostAddress ?: "")?.let { Cidr(it, la.prefixLength) } }
        val v6 = lp?.linkAddresses.orEmpty()
            .filter { it.address is Inet6Address }
            .map { "${it.address.hostAddress?.substringBefore('%')}/${it.prefixLength}" }
        val gateway = lp?.routes.orEmpty()
            .firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress
        val dns = lp?.dnsServers.orEmpty().mapNotNull { it.hostAddress?.substringBefore('%') }
        val wifi = if (transport == "Wi-Fi") wifiInfo(caps) else null
        val ssid = wifi?.ssid?.trim('"')?.takeUnless { it.isBlank() || it == "<unknown ssid>" || it == WifiManager.UNKNOWN_SSID }
        return NetInfo(
            transport = transport,
            interfaceName = lp?.interfaceName,
            ipv4 = v4,
            ipv6 = v6,
            gateway = gateway,
            dns = dns,
            ssid = ssid,
            rssi = wifi?.rssi?.takeIf { it > -127 },
            linkSpeedMbps = wifi?.linkSpeed?.takeIf { it > 0 },
            frequencyMhz = wifi?.frequency?.takeIf { it > 0 },
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            downKbps = caps.linkDownstreamBandwidthKbps,
            upKbps = caps.linkUpstreamBandwidthKbps,
        )
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun wifiInfo(caps: NetworkCapabilities): WifiInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (caps.transportInfo as? WifiInfo)?.let { return it }
        }
        return runCatching { app.getSystemService(WifiManager::class.java)?.connectionInfo }.getOrNull()
    }

    /** Rozwiązuje nazwę przez wybraną sieć (DNS tej sieci). */
    suspend fun resolve(host: String): List<InetAddress> = withContext(Dispatchers.IO) {
        val network = currentNetwork()
        (network?.getAllByName(host) ?: InetAddress.getAllByName(host)).toList()
    }
}
