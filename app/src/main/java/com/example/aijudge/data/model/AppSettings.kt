package com.example.aijudge.data.model

data class AppSettings(
    val llmApiKey: String = "",
    val llmEndpoint: String = "https://api.openai.com/v1",
    val llmModel: String = "gpt-3.5-turbo",
    val sttModelPath: String = "", // Path or identifier for Sherpa model
    val ttsModelPath: String = "",
    val systemPrompt: String = "You are an AI Judge. You listen to the environment and give short, witty judgments."
)
