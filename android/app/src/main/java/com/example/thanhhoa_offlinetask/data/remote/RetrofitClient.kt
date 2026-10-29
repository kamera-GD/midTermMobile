
package com.example.thanhhoa_offlinetask.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object RetrofitClient {

    /**
     * Tự động dò và kết nối qua tất cả các địa chỉ máy chủ khả dụng:
     * 1. 10.0.2.2      : Địa chỉ mặc định cho Android Emulator nối về localhost máy tính (Không cần adb reverse)
     * 2. 192.168.1.148 : Địa chỉ IP Wi-Fi LAN của máy tính (Cho điện thoại thật cùng mạng Wi-Fi, không cần adb reverse)
     * 3. 127.0.0.1     : Địa chỉ loopback khi cắm cáp USB dùng adb reverse
     */
    private val CANDIDATE_HOSTS = listOf(
        "10.0.2.2",
        "192.168.1.148",
        "127.0.0.1"
    )

    private const val PORT = 3000
    private const val BASE_URL = "http://10.0.2.2:3000/"

    @Volatile
    private var preferredHost: String = CANDIDATE_HOSTS.first()

    private val multiHostFallbackInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val orderedHosts = listOf(preferredHost) + CANDIDATE_HOSTS.filter { it != preferredHost }
        var lastException: IOException? = null

        for (host in orderedHosts) {
            val newUrl = originalRequest.url().newBuilder()
                .scheme("http")
                .host(host)
                .port(PORT)
                .build()

            val newRequest = originalRequest.newBuilder()
                .url(newUrl)
                .build()

            try {
                val response: Response = chain.proceed(newRequest)
                preferredHost = host
                return@Interceptor response
            } catch (e: IOException) {
                lastException = e
            }
        }

        throw lastException ?: IOException("Không thể kết nối tới máy chủ Backend")
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .addInterceptor(multiHostFallbackInterceptor)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: TaskApiService =
        retrofit.create(TaskApiService::class.java)
}
