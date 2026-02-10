package com.weelo.logistics.core.di

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.weelo.logistics.core.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import com.weelo.logistics.core.security.RateLimitInterceptor
import com.weelo.logistics.core.security.CertificatePinnerConfig
import com.weelo.logistics.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module for application dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .create()
    }

    @Provides
    @Singleton
    fun provideRateLimitInterceptor(): RateLimitInterceptor {
        return RateLimitInterceptor()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        tokenManager: com.weelo.logistics.data.remote.TokenManager,
        apiServiceLazy: dagger.Lazy<com.weelo.logistics.data.remote.api.WeeloApiService>,
        rateLimitInterceptor: RateLimitInterceptor
    ): OkHttpClient {
        // Token refresh authenticator for automatic 401 handling
        val authenticator = com.weelo.logistics.data.remote.TokenRefreshAuthenticator(
            tokenManager, apiServiceLazy
        )
        
        // Auth interceptor to add token to requests
        val authInterceptor = com.weelo.logistics.data.remote.AuthInterceptor(tokenManager)
        // =============================================================
        // DETAILED LOGGING INTERCEPTOR
        // Logs full request/response bodies for debugging
        // =============================================================
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            // Log to both Timber and Android Log for maximum visibility
            Timber.tag("OkHttp").d(message)
            timber.log.Timber.d(message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // =============================================================
        // CUSTOM REQUEST LOGGING INTERCEPTOR
        // Captures and logs the exact request being sent (headers + body)
        // IMPORTANT: Must not consume request body - use copy!
        // =============================================================
        val requestDebugInterceptor = okhttp3.Interceptor { chain ->
            val originalRequest = chain.request()
            
            // Log request details
            val logBuilder = StringBuilder()
            logBuilder.appendLine("╔══════════════════════════════════════════════════════════════")
            logBuilder.appendLine("║ 📤 OUTGOING REQUEST")
            logBuilder.appendLine("╠══════════════════════════════════════════════════════════════")
            logBuilder.appendLine("║ URL: ${originalRequest.url}")
            logBuilder.appendLine("║ Method: ${originalRequest.method}")
            logBuilder.appendLine("║ Headers:")
            originalRequest.headers.forEach { (name, value) ->
                val displayValue = if (name.equals("Authorization", ignoreCase = true)) {
                    "${value.take(20)}... [REDACTED]"
                } else value
                logBuilder.appendLine("║   $name: $displayValue")
            }
            
            // Log request body WITHOUT consuming it
            // We read into a buffer, then use that string to both log AND recreate the body
            var bodyString: String? = null
            val requestBody = originalRequest.body
            if (requestBody != null && requestBody.contentLength() != 0L) {
                val buffer = okio.Buffer()
                requestBody.writeTo(buffer)
                bodyString = buffer.readUtf8()  // Read the body content
                logBuilder.appendLine("║ Content-Type: ${requestBody.contentType()}")
                logBuilder.appendLine("║ Content-Length: ${requestBody.contentLength()}")
                logBuilder.appendLine("║ Body:")
                // Pretty print JSON if possible
                try {
                    val jsonObject = org.json.JSONObject(bodyString)
                    logBuilder.appendLine("║ ${jsonObject.toString(2).replace("\n", "\n║ ")}")
                } catch (e: Exception) {
                    logBuilder.appendLine("║ $bodyString")
                }
            } else {
                logBuilder.appendLine("║ Body: [empty]")
            }
            logBuilder.appendLine("╚══════════════════════════════════════════════════════════════")
            
            Timber.tag("WeeloRequest").i(logBuilder.toString())
            timber.log.Timber.i(logBuilder.toString())
            
            // CRITICAL: Rebuild the request with fresh body since we consumed it
            val newRequest = if (bodyString != null && requestBody != null) {
                originalRequest.newBuilder()
                    .method(originalRequest.method, 
                        bodyString.toRequestBody(requestBody.contentType()))
                    .build()
            } else {
                originalRequest
            }
            
            // Execute request and log response
            val startTime = System.currentTimeMillis()
            val response = chain.proceed(newRequest)
            val duration = System.currentTimeMillis() - startTime
            
            // Log response
            val responseLogBuilder = StringBuilder()
            responseLogBuilder.appendLine("╔══════════════════════════════════════════════════════════════")
            responseLogBuilder.appendLine("║ 📥 INCOMING RESPONSE (${duration}ms)")
            responseLogBuilder.appendLine("╠══════════════════════════════════════════════════════════════")
            responseLogBuilder.appendLine("║ URL: ${response.request.url}")
            responseLogBuilder.appendLine("║ Status: ${response.code} ${response.message}")
            responseLogBuilder.appendLine("║ Headers:")
            response.headers.forEach { (name, value) ->
                responseLogBuilder.appendLine("║   $name: $value")
            }
            
            // Log response body (using buffer clone - safe way)
            val responseBody = response.body
            if (responseBody != null) {
                val source = responseBody.source()
                source.request(Long.MAX_VALUE)
                val buffer = source.buffer.clone()  // Clone to avoid consuming
                val respBodyString = buffer.readUtf8()
                responseLogBuilder.appendLine("║ Body:")
                // Pretty print JSON if possible
                try {
                    if (respBodyString.startsWith("{")) {
                        val jsonObject = org.json.JSONObject(respBodyString)
                        responseLogBuilder.appendLine("║ ${jsonObject.toString(2).replace("\n", "\n║ ")}")
                    } else if (respBodyString.startsWith("[")) {
                        val jsonArray = org.json.JSONArray(respBodyString)
                        responseLogBuilder.appendLine("║ ${jsonArray.toString(2).replace("\n", "\n║ ")}")
                    } else {
                        responseLogBuilder.appendLine("║ $respBodyString")
                    }
                } catch (e: Exception) {
                    responseLogBuilder.appendLine("║ $respBodyString")
                }
            }
            responseLogBuilder.appendLine("╚══════════════════════════════════════════════════════════════")
            
            Timber.tag("WeeloResponse").i(responseLogBuilder.toString())
            timber.log.Timber.i(responseLogBuilder.toString())
            
            response
        }

        // Build OkHttp client with security features
        val builder = OkHttpClient.Builder()
            .addInterceptor(rateLimitInterceptor)     // Rate limiting (security)
            .addInterceptor(authInterceptor)          // Add auth token to requests
            .authenticator(authenticator)             // Handle 401 with token refresh
            .connectTimeout(Constants.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(Constants.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(Constants.WRITE_TIMEOUT, TimeUnit.SECONDS)
        
        // Add certificate pinning if enabled (for MITM protection)
        CertificatePinnerConfig.buildPinner()?.let { pinner ->
            builder.certificatePinner(pinner)
        }
        
        // Add debug logging only in debug builds (stripped in release by ProGuard)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(requestDebugInterceptor)
            builder.addInterceptor(loggingInterceptor)
        }
        
        return builder.build()
    }

    @Provides
    @Singleton
    @Named("weelo")
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    // NOTE: Google Maps Retrofit removed - now using AWS Location via backend
    // All geocoding and routing goes through WeeloApiService

    @Provides
    @Singleton
    fun provideWeeloApiService(@Named("weelo") retrofit: Retrofit): com.weelo.logistics.data.remote.api.WeeloApiService {
        return retrofit.create(com.weelo.logistics.data.remote.api.WeeloApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): com.weelo.logistics.data.remote.TokenManager {
        return com.weelo.logistics.data.remote.TokenManager(context)
    }

    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context {
        return context
    }
    
    // =========================================================================
    // NETWORK RESILIENCE
    // =========================================================================
    
    @Provides
    @Singleton
    fun provideNetworkMonitor(@ApplicationContext context: Context): com.weelo.logistics.core.util.NetworkMonitor {
        return com.weelo.logistics.core.util.NetworkMonitor.getInstance(context)
    }
    
    @Provides
    @Singleton
    fun provideCircuitBreaker(): com.weelo.logistics.core.network.CircuitBreaker {
        return com.weelo.logistics.core.network.CircuitBreaker().apply {
            // Configure for production use
            configure(
                failureThreshold = 5,
                resetTimeoutMs = 30_000L,
                successThreshold = 2
            )
        }
    }
    
    @Provides
    @Singleton
    fun provideResilientApiExecutor(
        circuitBreaker: com.weelo.logistics.core.network.CircuitBreaker,
        networkMonitor: com.weelo.logistics.core.util.NetworkMonitor
    ): com.weelo.logistics.core.network.ResilientApiExecutor {
        return com.weelo.logistics.core.network.ResilientApiExecutor(circuitBreaker, networkMonitor)
    }
}
