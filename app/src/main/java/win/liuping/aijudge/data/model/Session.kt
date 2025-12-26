package win.liuping.aijudge.data.model

import java.util.UUID

data class Session(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<Message> = emptyList(),
    val speakerAliases: Map<String, String> = emptyMap() // "Speaker 1" -> "张三"
) {
    fun getDisplayTitle(): String {
        return title.ifBlank {
            // Use first message content as title, or creation date
            messages.firstOrNull { it.sender == Sender.USER }?.content?.take(20)
                ?: java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(createdAt))
        }
    }

    fun getSpeakerDisplayName(speakerId: String?): String? {
        if (speakerId == null) return null
        return speakerAliases[speakerId] ?: speakerId
    }
}
