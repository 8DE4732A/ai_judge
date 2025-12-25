package win.liuping.aijudge.data.network

import win.liuping.aijudge.data.network.model.ChatCompletionRequest
import win.liuping.aijudge.data.network.model.ChatCompletionResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface LlmApi {
    @POST
    suspend fun chatCompletion(
        @Url url: String, // Dynamic URL to support switching endpoints
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}
