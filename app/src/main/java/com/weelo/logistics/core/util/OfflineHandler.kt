package com.weelo.logistics.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * OfflineHandler - Network connectivity monitoring and offline support
 * 
 * SCALABILITY:
 * - Singleton pattern for app-wide usage
 * - LiveData/StateFlow for reactive updates
 * - Works with millions of users (client-side only)
 * 
 * FEATURES:
 * - Real-time connectivity monitoring
 * - Offline mode detection
 * - Automatic retry queue (optional)
 * - Connection type detection (WiFi, Mobile, etc.)
 * 
 * Usage:
 * ```
 * // In Application class
 * OfflineHandler.initialize(applicationContext)
 * 
 * // In Activity/ViewModel
 * OfflineHandler.instance.networkState.observe(this) { state ->
 *     when (state) {
 *         NetworkState.Connected -> enableOnlineFeatures()
 *         NetworkState.Disconnected -> showOfflineMode()
 *     }
 * }
 * ```
 */
class OfflineHandler private constructor(private val context: Context) {

    private val connectivityManager = 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // LiveData for Activity/Fragment observation
    private val _networkStateLiveData = MutableLiveData<NetworkState>(NetworkState.Unknown)
    val networkStateLiveData: LiveData<NetworkState> = _networkStateLiveData

    // StateFlow for ViewModel/Coroutine usage
    private val _networkStateFlow = MutableStateFlow<NetworkState>(NetworkState.Unknown)
    val networkStateFlow: StateFlow<NetworkState> = _networkStateFlow.asStateFlow()

    // Simple boolean check
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    // Pending actions queue for offline retry
    private val pendingActions = mutableListOf<PendingAction>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Timber.d("Network available")
            updateNetworkState(NetworkState.Connected)
            processPendingActions()
        }

        override fun onLost(network: Network) {
            Timber.d("Network lost")
            updateNetworkState(NetworkState.Disconnected)
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val connectionType = when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 
                    ConnectionType.WIFI
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 
                    ConnectionType.CELLULAR
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 
                    ConnectionType.ETHERNET
                else -> ConnectionType.OTHER
            }
            
            val hasInternet = networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            )
            
            if (hasInternet) {
                updateNetworkState(NetworkState.Connected, connectionType)
            }
        }
    }

    init {
        registerNetworkCallback()
        checkInitialState()
    }

    private fun registerNetworkCallback() {
        try {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        } catch (e: Exception) {
            Timber.e(e, "Failed to register network callback")
        }
    }

    private fun checkInitialState() {
        val isConnected = isNetworkAvailable()
        updateNetworkState(
            if (isConnected) NetworkState.Connected else NetworkState.Disconnected
        )
    }

    @Suppress("UNUSED_PARAMETER") // connectionType reserved for future use
    private fun updateNetworkState(
        state: NetworkState, 
        connectionType: ConnectionType = ConnectionType.UNKNOWN
    ) {
        _networkStateLiveData.postValue(state)
        _networkStateFlow.value = state
        _isOnline.value = state == NetworkState.Connected
    }

    /**
     * Check if network is currently available
     */
    fun isNetworkAvailable(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            Timber.e(e, "Error checking network")
            false
        }
    }

    /**
     * Add action to retry when back online
     */
    fun addPendingAction(action: PendingAction) {
        synchronized(pendingActions) {
            pendingActions.add(action)
            Timber.d("Added pending action: ${action.id}")
        }
    }

    /**
     * Process pending actions when online
     */
    private fun processPendingActions() {
        synchronized(pendingActions) {
            val actionsToProcess = pendingActions.toList()
            pendingActions.clear()
            
            actionsToProcess.forEach { action ->
                try {
                    Timber.d("Processing pending action: ${action.id}")
                    action.execute()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to process pending action: ${action.id}")
                    // Re-add failed actions
                    if (action.retryCount < action.maxRetries) {
                        action.retryCount++
                        pendingActions.add(action)
                    }
                }
            }
        }
    }

    /**
     * Execute action with offline handling
     * Returns cached result if offline
     */
    @Suppress("UNUSED_PARAMETER") // cacheKey reserved for future caching implementation
    inline fun <T> executeWithOfflineSupport(
        cacheKey: String? = null,
        cachedValue: T? = null,
        crossinline action: () -> T
    ): com.weelo.logistics.core.common.Result<T> {
        return if (isNetworkAvailable()) {
            try {
                com.weelo.logistics.core.common.Result.Success(action())
            } catch (e: Exception) {
                if (cachedValue != null) {
                    com.weelo.logistics.core.common.Result.Success(cachedValue)
                } else {
                    com.weelo.logistics.core.common.Result.Error(e, e.message)
                }
            }
        } else {
            if (cachedValue != null) {
                com.weelo.logistics.core.common.Result.Success(cachedValue)
            } else {
                com.weelo.logistics.core.common.Result.Error(OfflineException("No network connection"), "No network connection")
            }
        }
    }

    /**
     * Cleanup - call when app is destroyed
     */
    fun cleanup() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Timber.e(e, "Error unregistering network callback")
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: OfflineHandler? = null

        fun initialize(context: Context): OfflineHandler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OfflineHandler(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        val instance: OfflineHandler
            get() = INSTANCE ?: throw IllegalStateException(
                "OfflineHandler not initialized. Call initialize() first."
            )
    }
}

/**
 * Network state enum
 */
sealed class NetworkState {
    object Connected : NetworkState()
    object Disconnected : NetworkState()
    object Unknown : NetworkState()
}

/**
 * Connection type enum
 */
enum class ConnectionType {
    WIFI, CELLULAR, ETHERNET, OTHER, UNKNOWN
}

/**
 * Pending action for offline retry
 */
data class PendingAction(
    val id: String,
    val maxRetries: Int = 3,
    var retryCount: Int = 0,
    val execute: () -> Unit
)

/**
 * Offline exception
 */
class OfflineException(message: String) : Exception(message)
