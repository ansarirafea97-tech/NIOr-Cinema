package com.example

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.network.HttpException
import coil.request.ErrorResult
import coil.request.ImageRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Custom OkHttp DNS Resolver designed for seamless Wi-Fi and Mobile Data (Cellular 4G/5G/LTE) operation.
 *
 * Mobile Data Root Causes Addressed:
 * 1. Carrier UDP DNS blocking / poisoning / sinkholing for `api.themoviedb.org` and `image.tmdb.org`
 *    (returning UnknownHostException or bogon/private/loopback IPs like 0.0.0.0, 127.0.0.1, 10.x.x.x, 192.168.x.x).
 * 2. IPv6-only / 464XLAT cellular networks where forcing IPv4-only before IPv6 or using unbound system DNS fails,
 *    or dual-stack networks where one address family stalls.
 * 3. Automatic DNS-over-HTTPS (DoH) resolution via Cloudflare (1.1.1.1) and Google (8.8.8.8) using direct IP
 *    literals (requiring zero UDP DNS lookup) whenever carrier DNS fails, returns sinkhole IPs, or when a host
 *    experiences a connection failure on mobile data.
 */
class SmartDualStackDns : Dns {

    companion object {
        private const val TAG = "TMDB_NET_DEBUG"
        private const val DOH_CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutes

        private data class CachedDnsEntry(
            val timestampMs: Long,
            val addresses: List<InetAddress>
        )

        private val dohCache = ConcurrentHashMap<String, CachedDnsEntry>()
        private val dohPreferredHosts = ConcurrentHashMap.newKeySet<String>()

        /**
         * Dedicated bootstrap OkHttpClient for DNS-over-HTTPS queries to literal IPs (1.1.1.1 / 8.8.8.8).
         * Uses direct IP URLs so it never triggers recursive DNS resolution.
         */
        private val dohHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .writeTimeout(4, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                .build()
        }

        fun markHostForDohFallback(hostname: String) {
            if (hostname.isNotBlank()) {
                dohPreferredHosts.add(hostname.lowercase())
            }
        }

        fun clearTransientDnsState() {
            // Keep valid DoH cache entries across network switches, but allow fresh active-network lookups
            val now = System.currentTimeMillis()
            dohCache.entries.removeIf { now - it.value.timestampMs > DOH_CACHE_TTL_MS }
        }

        /**
         * Filters out bogon, loopback, unspecified (0.0.0.0 / ::), link-local, and private/site-local
         * sinkhole addresses often injected by cellular carrier DNS filters.
         */
        fun isUsablePublicAddress(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress ||
                address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||
                address.isMulticastAddress
            ) {
                return false
            }
            if (address is Inet4Address) {
                val bytes = address.address
                if (bytes.size == 4) {
                    val b0 = bytes[0].toInt() and 0xFF
                    val b1 = bytes[1].toInt() and 0xFF
                    // Filter 0.x.x.x, 127.x.x.x, and carrier grade NAT 100.64.0.0/10 returned as fake target host
                    if (b0 == 0 || b0 == 127) return false
                    if (b0 == 100 && (b1 in 64..127)) return false
                }
            } else if (address is Inet6Address) {
                val bytes = address.address
                if (bytes.size == 16) {
                    val b0 = bytes[0].toInt() and 0xFF
                    val b1 = bytes[1].toInt() and 0xFF
                    // Filter IPv6 Unique Local Addresses (fc00::/7) and documentation/discard ranges
                    if ((b0 and 0xFE) == 0xFC) return false
                    if (b0 == 0x01 && b1 == 0x00) return false // 100::/64 discard prefix
                }
            }
            return true
        }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val normalizedHost = hostname.trim().lowercase()
        val isTmdbHost = normalizedHost.endsWith("themoviedb.org") || normalizedHost.endsWith("tmdb.org")
        val preferDohFirst = dohPreferredHosts.contains(normalizedHost) || (isTmdbHost && isActiveNetworkCellular())

        val collected = mutableListOf<InetAddress>()

        // 1. If on mobile data for TMDB or host previously failed over carrier DNS, query DoH first
        if (preferDohFirst) {
            val dohAddresses = resolveViaDoh(normalizedHost)
            collected.addAll(dohAddresses)
        }

        // 2. Query Android's active Network directly (ensures cellular interface DNS is used when on mobile data)
        try {
            val app = if (InfuseApplication.isInitialized()) InfuseApplication.instance else null
            val cm = app?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNet = cm?.activeNetwork
            if (activeNet != null) {
                val netAddrs = activeNet.getAllByName(hostname)
                    .filter { isUsablePublicAddress(it) }
                collected.addAll(netAddrs)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Active network DNS lookup failed for $hostname: ${e.message}")
            }
        }

        // 3. Query standard Dns.SYSTEM and filter out carrier sinkhole/bogon IPs
        try {
            val sysAddrs = Dns.SYSTEM.lookup(hostname)
                .filter { isUsablePublicAddress(it) }
            collected.addAll(sysAddrs)
        } catch (e: UnknownHostException) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "System DNS lookup failed for $hostname: ${e.message}")
            }
        }

        // 4. Fallback to InetAddress.getAllByName if needed
        if (collected.isEmpty()) {
            try {
                val fallbackAddrs = InetAddress.getAllByName(hostname)
                    .filter { isUsablePublicAddress(it) }
                collected.addAll(fallbackAddrs)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "InetAddress fallback lookup failed for $hostname: ${e.message}")
                }
            }
        }

        // 5. If system/carrier DNS returned nothing usable (or for TMDB hosts where DoH wasn't queried yet), resolve via DoH
        if (collected.isEmpty() || (!preferDohFirst && isTmdbHost)) {
            val dohAddresses = resolveViaDoh(normalizedHost)
            collected.addAll(dohAddresses)
        }

        val distinctAddresses = collected.distinctBy { it.hostAddress }
        if (distinctAddresses.isEmpty()) {
            throw UnknownHostException("Unable to resolve host \"$hostname\": No usable address associated with hostname")
        }

        return orderAddressesForActiveNetwork(distinctAddresses)
    }

    private fun isActiveNetworkCellular(): Boolean {
        return try {
            if (!InfuseApplication.isInitialized()) return false
            val cm = InfuseApplication.instance.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNet = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNet) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Resolves A (IPv4) and AAAA (IPv6) records over HTTPS using Cloudflare (1.1.1.1 / 1.0.0.1)
     * and Google (8.8.8.8) JSON DoH endpoints by IP literal so carrier UDP DNS cannot block or poison the lookup.
     */
    private fun resolveViaDoh(hostname: String): List<InetAddress> {
        val now = System.currentTimeMillis()
        dohCache[hostname]?.let { cached ->
            if (now - cached.timestampMs < DOH_CACHE_TTL_MS && cached.addresses.isNotEmpty()) {
                return cached.addresses
            }
        }

        val resolved = mutableListOf<InetAddress>()
        val endpoints = listOf(
            "https://1.1.1.1/dns-query?name=$hostname&type=A",
            "https://1.1.1.1/dns-query?name=$hostname&type=AAAA",
            "https://8.8.8.8/resolve?name=$hostname&type=A",
            "https://8.8.8.8/resolve?name=$hostname&type=AAAA",
            "https://1.0.0.1/dns-query?name=$hostname&type=A"
        )

        for (url in endpoints) {
            // Stop once we have both IPv4 and IPv6 or enough healthy records from the primary DoH endpoint
            if (resolved.any { it is Inet4Address } && resolved.any { it is Inet6Address }) {
                break
            }
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/dns-json")
                    .build()
                dohHttpClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val json = JSONObject(body)
                            val answers = json.optJSONArray("Answer")
                            if (answers != null) {
                                for (i in 0 until answers.length()) {
                                    val ans = answers.optJSONObject(i) ?: continue
                                    val type = ans.optInt("type", 0)
                                    val data = ans.optString("data", "").trim()
                                    if ((type == 1 || type == 28) && data.isNotEmpty()) {
                                        try {
                                            val addr = InetAddress.getByName(data)
                                            // Re-wrap with original hostname so TLS SNI and hostname verification work properly
                                            val namedAddr = InetAddress.getByAddress(hostname, addr.address)
                                            if (isUsablePublicAddress(namedAddr)) {
                                                resolved.add(namedAddr)
                                            }
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "DoH query failed for $url: ${e.message}")
                }
            }
        }

        val distinct = resolved.distinctBy { it.hostAddress }
        if (distinct.isNotEmpty()) {
            dohCache[hostname] = CachedDnsEntry(now, distinct)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DoH resolved ${distinct.size} addresses for $hostname: ${distinct.joinToString { it.hostAddress ?: "" }}")
            }
        }
        return distinct
    }

    /**
     * Interleaves IPv4 and IPv6 addresses so OkHttp's RouteSelector can seamlessly connect on
     * IPv4-only, IPv6-only (cellular 4G/5G NAT64/464XLAT), and dual-stack networks without getting
     * stuck behind a single unresponsive address family.
     */
    private fun orderAddressesForActiveNetwork(addresses: List<InetAddress>): List<InetAddress> {
        val (ipv4, ipv6) = addresses.partition { it is Inet4Address }
        if (ipv4.isEmpty()) return ipv6
        if (ipv6.isEmpty()) return ipv4

        val preferIpv6First = try {
            val app = if (InfuseApplication.isInitialized()) InfuseApplication.instance else null
            val cm = app?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNet = cm?.activeNetwork
            val linkProps = if (activeNet != null) cm.getLinkProperties(activeNet) else null
            if (linkProps != null) {
                val hasGlobalIpv4 = linkProps.linkAddresses.any { it.address is Inet4Address && !it.address.isLoopbackAddress }
                val hasGlobalIpv6 = linkProps.linkAddresses.any { it.address is Inet6Address && !it.address.isLinkLocalAddress }
                // On pure IPv6 cellular links without native IPv4, lead with IPv6 then interleave IPv4 (for 464XLAT clatd)
                hasGlobalIpv6 && !hasGlobalIpv4
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }

        val interleaved = mutableListOf<InetAddress>()
        val maxLen = maxOf(ipv4.size, ipv6.size)
        for (i in 0 until maxLen) {
            if (preferIpv6First) {
                if (i < ipv6.size) interleaved.add(ipv6[i])
                if (i < ipv4.size) interleaved.add(ipv4[i])
            } else {
                if (i < ipv4.size) interleaved.add(ipv4[i])
                if (i < ipv6.size) interleaved.add(ipv6[i])
            }
        }
        return interleaved
    }
}

class InfuseApplication : Application(), ImageLoaderFactory {

    companion object {
        private const val TAG = "InfuseApplication"
        private const val NET_DEBUG_TAG = "TMDB_NET_DEBUG"

        lateinit var instance: InfuseApplication
            private set

        fun isInitialized(): Boolean = ::instance.isInitialized

        private val sharedConnectionPool = ConnectionPool(10, 45, TimeUnit.SECONDS)

        private val _networkGeneration = MutableStateFlow(0)
        /**
         * Emits an incrementing counter whenever network availability or transport changes
         * (e.g., switching between Wi-Fi and Mobile Data), enabling UI image requests and
         * metadata jobs to automatically retry without restarting the app.
         */
        val networkGeneration: StateFlow<Int> = _networkGeneration.asStateFlow()

        /**
         * Shared OkHttpClient configured for seamless network routing over both Wi-Fi and Mobile Data (4G/5G/LTE).
         */
        val sharedOkHttpClient: OkHttpClient by lazy {
            val cacheDirectory = try {
                if (isInitialized()) {
                    File(instance.cacheDir, "http_response_cache").apply { mkdirs() }
                } else null
            } catch (e: Exception) {
                null
            }

            val builder = OkHttpClient.Builder()
                .dns(SmartDualStackDns())
                .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .connectionPool(sharedConnectionPool)
                .addInterceptor { chain: Interceptor.Chain ->
                    val original = chain.request()
                    val reqBuilder = original.newBuilder()
                    if (original.header("User-Agent") == null) {
                        reqBuilder.header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                        )
                    }
                    if (original.header("Accept") == null) {
                        reqBuilder.header("Accept", "application/json, image/*, */*")
                    }
                    if (original.header("Accept-Language") == null) {
                        reqBuilder.header("Accept-Language", "en-US,en;q=0.9")
                    }
                    val request = reqBuilder.build()

                    var lastException: IOException? = null
                    val maxAttempts = 3

                    for (attempt in 1..maxAttempts) {
                        try {
                            val response = chain.proceed(request)
                            if (!response.isSuccessful && BuildConfig.DEBUG) {
                                Log.w(
                                    NET_DEBUG_TAG,
                                    "HTTP ${response.code} (${response.message}) for ${request.method} ${request.url}"
                                )
                            }
                            // Retry transient 502/503/504 gateway errors on mobile data
                            if (response.code in listOf(502, 503, 504) && attempt < maxAttempts) {
                                response.close()
                                Thread.sleep(250L * attempt)
                                continue
                            }
                            return@addInterceptor response
                        } catch (e: IOException) {
                            lastException = e
                            val host = request.url.host
                            // Mark host for DNS-over-HTTPS fallback so retry uses verified IPs
                            SmartDualStackDns.markHostForDohFallback(host)
                            if (attempt < maxAttempts) {
                                try {
                                    sharedConnectionPool.evictAll()
                                } catch (_: Exception) {
                                }
                                if (BuildConfig.DEBUG) {
                                    Log.w(
                                        NET_DEBUG_TAG,
                                        "Retrying ${request.url} (attempt $attempt/$maxAttempts) after ${e.javaClass.simpleName}: ${e.message}"
                                    )
                                }
                                try {
                                    Thread.sleep(250L * attempt)
                                } catch (_: InterruptedException) {
                                    Thread.currentThread().interrupt()
                                    throw e
                                }
                            } else {
                                if (BuildConfig.DEBUG) {
                                    Log.e(
                                        NET_DEBUG_TAG,
                                        "Network IOException for ${request.method} ${request.url}: ${e.javaClass.simpleName}: ${e.message}",
                                        e
                                    )
                                }
                                throw e
                            }
                        }
                    }

                    throw lastException ?: IOException("Request failed for ${request.url}")
                }

            if (cacheDirectory != null) {
                try {
                    builder.cache(Cache(cacheDirectory, 60L * 1024 * 1024)) // 60 MB for API responses
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to initialize HTTP cache: ${e.message}")
                }
            }

            builder.build()
        }

        /**
         * Dedicated OkHttpClient for Coil image loading that shares the underlying
         * dispatcher, interceptors, DNS resolver, and connection pool with [sharedOkHttpClient],
         * but disables OkHttp's HTTP cache as required by Coil (which manages its own DiskCache).
         */
        val coilOkHttpClient: OkHttpClient by lazy {
            sharedOkHttpClient.newBuilder()
                .cache(null)
                .build()
        }

        /**
         * Evicts pooled idle sockets when switching networks (e.g., Wi-Fi <-> Mobile Data)
         * so new requests immediately bind to the active network interface instead of
         * hanging on stale sockets from a previous interface.
         */
        fun evictIdleConnections(reason: String = "network_change") {
            try {
                sharedConnectionPool.evictAll()
                SmartDualStackDns.clearTransientDnsState()
                _networkGeneration.value = _networkGeneration.value + 1
                if (BuildConfig.DEBUG) {
                    Log.d(NET_DEBUG_TAG, "Evicted idle OkHttp connections ($reason), generation=${_networkGeneration.value}")
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(NET_DEBUG_TAG, "Failed to evict idle connections: ${e.message}")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            Coil.setImageLoader(newImageLoader())
        } catch (e: Exception) {
            Log.w(TAG, "Could not eagerly set Coil ImageLoader: ${e.message}")
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { coilOkHttpClient }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_image_cache"))
                    .maxSizeBytes(120L * 1024 * 1024) // 120 MB disk cache for offline & mobile data persistence
                    .build()
            }
            .respectCacheHeaders(false) // Cache immutable CDN poster/backdrop images reliably
            .crossfade(true)
            .eventListener(object : EventListener {
                override fun onError(request: ImageRequest, result: ErrorResult) {
                    if (BuildConfig.DEBUG) {
                        val throwable = result.throwable
                        val statusInfo = if (throwable is HttpException) {
                            " [HTTP ${throwable.response.code} ${throwable.response.message}]"
                        } else {
                            ""
                        }
                        Log.e(
                            NET_DEBUG_TAG,
                            "Coil image load failed for data='${request.data}'$statusInfo: ${throwable.javaClass.simpleName}: ${throwable.message}",
                            throwable
                        )
                    }
                }
            })
            .build()
    }
}
