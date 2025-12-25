package win.liuping.aijudge.data.model

data class Message(
    val id: String,
    val sender: Sender,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class Sender {
    USER, // The person speaking in the environment
    JUDGE, // The AI Judge
    SYSTEM // System messages (e.g. status)
}
