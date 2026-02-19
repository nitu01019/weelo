package com.weelo.logistics.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import timber.log.Timber
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * =============================================================================
 * NETWORK MONITOR - Real-time Connectivity Status
 * =============================================================================
 * 
 * Monitors network connectivity and provides real-time updates.
 * 
 * FEATURES:
 * - Real-time connectivity status
 * - Network type detection (WiFi, Cellular, etc.)
 * - Callback-based and Flow-based APIs
 * - Singleton pattern for app-wide use
 * 
 * USAGE:
 * ```kotlin
 * // Get singleton instance
 * val networkMonitor = NetworkMonitor.getInstance(context)
 * 
 * // Check current status
 * if (networkMonitor.isOnline.value) {
 *     // Online
 * }
 * 
 * // Observe changes
 * lifecycleScope.launch {
 *     networkMonitor.isOnline.collect { isOnline ->
 *         if (!isOnline) showOfflineBanner()
 *     }
 * }
 * ```
 * =============================================================================
 */
class NetworkMonitor private constructor(context: Context) {
    
    private val TAG = "NetworkMonitor"
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
        as ConnectivityManager
    
    private val _isOnline = MutableStateFlow(checkCurrentConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    
    private val _networkType = MutableStateFlow(getCurrentNetworkType())
    val networkType: StateFlow<NetworkType> = _networkType.asStateFlow()
    
    /**
     * Network types
     */
    enum class NetworkType {
        WIFI,
        CELLULAR,
        ETHERNET,
        NONE,
        UNKNOWN
    }
    
    /**
     * Network callback for monitoring changes
     */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Timber.d("Network available")
            _isOnline.value = true
            _networkType.value = getCurrentNetworkType()
        }
        
        override fun onLost(network: Network) {
            Timber.d("Network lost")
            _isOnline.value = checkCurrentConnectivity()
            _networkType.value = getCurrentNetworkType()
        }
        
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val hasInternet = networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            ) && networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )
            _isOnline.value = hasInternet
            _networkType.value = getNetworkTypeFromCapabilities(networkCapabilities)
        }
    }
    
    init {
        // Register network callback
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            Timber.d("Network callback registered")
        } catch (e: Exception) {
            Timber.e(e, "Failed to register network callback")
        }
    }
    
    /**
     * Check current connectivity status
     */
    private fun checkCurrentConnectivity(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            Timber.e(e, "Error checking connectivity")
            false
        }
    }
    
    /**
     * Get current network type
     */
    private fun getCurrentNetworkType(): NetworkType {
        return try {
            val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
            val capabilities = connectivityManager.getNetworkCapabilities(network) 
                ?: return NetworkType.NONE
            
            getNetworkTypeFromCapabilities(capabilities)
        } catch (e: Exception) {
            Timber.e(e, "Error getting network type")
            NetworkType.UNKNOWN
        }
    }
    
    /**
     * Get network type from capabilities
     */
    private fun getNetworkTypeFromCapabilities(capabilities: NetworkCapabilities): NetworkType {
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.UNKNOWN
        }
    }
    
    /**
     * Check if currently online (non-flow version)
     */
    fun isCurrentlyOnline(): Boolean = _isOnline.value
    
    /**
     * Get network status as Flow (for compose)
     */
    fun observeNetworkStatus(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }
            
            override fun onLost(network: Network) {
                trySend(false)
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasInternet = networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                ) && networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                )
                trySend(hasInternet)
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, callback)
        
        // Send initial value
        trySend(checkCurrentConnectivity())
        
        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
    
    /**
     * Cleanup - call when app is destroyed
     */
    fun cleanup() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Timber.d("Network callback unregistered")
        } catch (e: Exception) {
            Timber.e(e, "Error unregistering callback")
        }
    }
    
    companion object {
        @Volatile
        private var INSTANCE: NetworkMonitor? = null
        
        /**
         * Get singleton instance
         */
        fun getInstance(context: Context): NetworkMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkMonitor(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}

/**
 * Extension to easily check if online from Context
 */
fun Context.isNetworkAvailable(): Boolean {
    return NetworkMonitor.getInstance(this).isCurrentlyOnline()
}
