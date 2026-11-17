package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class CnnClassifyRequest(
    @Json(name = "analysis_type") val analysisType: String,
    @Json(name = "image_base64") val imageBase64: String,
    @Json(name = "mime") val mime: String = "image/jpeg",
    @Json(name = "model_file") val modelFile: String? = null,
    @Json(name = "drive_folder_id") val driveFolderId: String? = null,
    @Json(name = "dataset_folder_id") val datasetFolderId: String? = null,
    @Json(name = "input_size") val inputSize: List<Int>? = null,
    @Json(name = "output_activation") val outputActivation: String? = null,
    @Json(name = "expects_normalized_input") val expectsNormalizedInput: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class CnnPredictionItem(
    val species: String? = null,
    val stage: String? = null,
    val disease: String? = null,
    val instar: String? = null,
    val defect: String? = null,
    val sex: String? = null,
    val confidence: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class CnnPredictionBlock(
    val predictions: List<CnnPredictionItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CnnClassifyResponse(
    val species: CnnPredictionBlock? = null,
    val stage: CnnPredictionBlock? = null,
    val disease: CnnPredictionBlock? = null,
    val instar: CnnPredictionBlock? = null,
    val defects: CnnPredictionBlock? = null,
    val sex: CnnPredictionBlock? = null
)

interface CnnApiService {
    @POST("api/classify-json")
    suspend fun classifyJson(@Body body: CnnClassifyRequest): CnnClassifyResponse

    @GET("api/status")
    suspend fun checkStatus(): Response<ResponseBody>

    @GET(".")
    suspend fun pingRoot(): Response<ResponseBody>

    companion object {
        fun create(baseUrl: String): CnnApiService {
            val trimmed = baseUrl.trim()
            require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                "Server URL must start with http:// or https://"
            }
            val normalized = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            return Retrofit.Builder()
                .baseUrl(normalized)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(CnnApiService::class.java)
        }
    }
}
