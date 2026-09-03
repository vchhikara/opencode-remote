package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiscoveredService(val name: String, val host: String, val port: Int)

class NsdHelper(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceType = "_opencode._tcp"
    
    private val _discoveredServices = MutableStateFlow<List<DiscoveredService>>(emptyList())
    val discoveredServices: StateFlow<List<DiscoveredService>> = _discoveredServices.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery() {
        if (discoveryListener != null) return
        
        _discoveredServices.value = emptyList()
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("NsdHelper", "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d("NsdHelper", "Service discovery success: $service")
                if (service.serviceType == serviceType || service.serviceType.contains("opencode")) {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e("NsdHelper", "Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            Log.d("NsdHelper", "Resolve Succeeded. $serviceInfo")
                            val host = serviceInfo.host.hostAddress
                            val port = serviceInfo.port
                            if (host != null) {
                                val discovered = DiscoveredService(serviceInfo.serviceName, host, port)
                                val current = _discoveredServices.value.toMutableList()
                                if (current.none { it.host == host && it.port == port }) {
                                    current.add(discovered)
                                    _discoveredServices.value = current
                                }
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.e("NsdHelper", "service lost: $service")
                val current = _discoveredServices.value.toMutableList()
                current.removeAll { it.name == service.serviceName }
                _discoveredServices.value = current
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i("NsdHelper", "Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("NsdHelper", "Discovery failed: Error code:$errorCode")
                nsdManager.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("NsdHelper", "Discovery failed: Error code:$errorCode")
                nsdManager.stopServiceDiscovery(this)
            }
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e("NsdHelper", "Failed to start discovery", e)
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e("NsdHelper", "Failed to stop discovery", e)
            }
        }
        discoveryListener = null
    }
}
