package me.timschneeberger.rootlessjamesdsp.api

import android.content.Context
import me.timschneeberger.rootlessjamesdsp.BuildConfig
import me.timschneeberger.rootlessjamesdsp.R
import me.timschneeberger.rootlessjamesdsp.flavor.CrashlyticsImpl
import me.timschneeberger.rootlessjamesdsp.model.api.AeqSearchResult
import me.timschneeberger.rootlessjamesdsp.utils.Constants
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit

class AutoEqClient(val context: Context, callTimeout: Long = 10, customBaseUrl: String? = null) {

    private val http = OkHttpClient
        .Builder()
        .callTimeout(callTimeout, TimeUnit.SECONDS)
        .addInterceptor(UserAgentInterceptor("RootlessJamesDSP v${BuildConfig.VERSION_NAME}"))
        .build()

    private val retrofit: Retrofit
    private val service: AutoEqService

    init {
        val apiUrl = customBaseUrl ?: context.getSharedPreferences(Constants.PREF_APP, Context.MODE_PRIVATE)
            .getString(context.getString(R.string.key_network_autoeq_api_url), DEFAULT_API_URL) ?: DEFAULT_API_URL
        Timber.i("Using API url: $apiUrl")
        CrashlyticsImpl.setCustomKey("aeq_api_url", apiUrl)

        retrofit = Retrofit.Builder()
            .baseUrl(apiUrl)
            .client(http)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        service = retrofit.create(AutoEqService::class.java)
    }

    fun queryProfiles(query: String, onResponse: ((Array<AeqSearchResult>, Boolean /* isPartialResult */) -> Unit), onFailure: ((String) -> Unit)?) {
        val call = service.queryProfiles(query)
        call.enqueue(object : Callback<Array<AeqSearchResult>> {
            override fun onResponse(call: Call<Array<AeqSearchResult>>, response: Response<Array<AeqSearchResult>>) {
                if (response.code() == 200) {
                    onResponse.invoke(response.body()!!, response.headers().get(HEADER_PARTIAL_RESULT) == "1")
                }
                else {
                    onFailure?.invoke(context.getString(R.string.geq_api_network_error,
                        context.getString(R.string.geq_api_network_error_details_code, response.code())
                    ))
                    Timber.e("queryProfiles: Server responded with ${response.code()} (${response.errorBody().toString()})")
                }
            }

            override fun onFailure(call: Call<Array<AeqSearchResult>>, t: Throwable) {
                Timber.e("queryProfiles: $t")
                onFailure?.invoke(context.getString(R.string.geq_api_network_error, t.localizedMessage))
            }
        })
    }

    fun getProfile(id: Long, onResponse: ((String, AeqSearchResult) -> Unit), onFailure: ((String) -> Unit)?) {
        val call = service.getProfile(id)
        call.enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                if (response.code() == 200) {
                    val result = AeqSearchResult(
                        response.headers().get(HEADER_PROFILE_NAME),
                        response.headers().get(HEADER_PROFILE_SOURCE),
                        response.headers().get(HEADER_PROFILE_RANK)?.toIntOrNull(),
                        response.headers().get(HEADER_PROFILE_ID)?.toLongOrNull()
                    )

                    onResponse.invoke(response.body()!!, result)
                }
                else {
                    onFailure?.invoke(context.getString(R.string.geq_api_network_error,
                        context.getString(R.string.geq_api_network_error_details_code, response.code())
                    ))
                    Timber.e("getProfile: Server responded with ${response.code()} (${response.errorBody().toString()})")
                }
            }

            override fun onFailure(call: Call<String>, t: Throwable) {
                Timber.e("getProfile: $t")
                onFailure?.invoke(context.getString(R.string.geq_api_network_error, t.localizedMessage))
            }
        })
    }

    companion object {
        const val DEFAULT_API_URL = "https://aeq.timschneeberger.me/"
        private const val HEADER_PARTIAL_RESULT = "X-Partial-Result"
        private const val HEADER_PROFILE_ID = "X-Profile-Id"
        private const val HEADER_PROFILE_NAME = "X-Profile-Name"
        private const val HEADER_PROFILE_SOURCE = "X-Profile-Source"
        private const val HEADER_PROFILE_RANK = "X-Profile-Rank"
    }
}