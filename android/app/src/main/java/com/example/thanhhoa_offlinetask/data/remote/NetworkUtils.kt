package com.example.thanhhoa_offlinetask.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkUtils {

    /**
     * Kiểm tra thiết bị có đang bật kết nối mạng thực sự (Wi-Fi / Cellular / Ethernet) hay không.
     * Giúp chặn việc đồng bộ lách qua cổng loopback 127.0.0.1 (adb reverse) khi người dùng đã tắt Wi-Fi / Mobile Data.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false

        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        val hasInternetCapability =
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        val hasRealTransport =
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

        return hasInternetCapability && hasRealTransport
    }
}
