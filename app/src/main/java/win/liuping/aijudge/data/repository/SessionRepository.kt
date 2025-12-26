package win.liuping.aijudge.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import win.liuping.aijudge.data.model.Message
import win.liuping.aijudge.data.model.Sender
import win.liuping.aijudge.data.model.Session
import java.io.File

class SessionRepository(private val context: Context) {
    private val sessionsDir = File(context.filesDir, "sessions")
    private val TAG = "SessionRepository"

    init {
        if (!sessionsDir.exists()) {
            sessionsDir.mkdirs()
        }
    }

    suspend fun saveSession(session: Session) = withContext(Dispatchers.IO) {
        try {
            val file = File(sessionsDir, "${session.id}.json")
            val json = sessionToJson(session)
            file.writeText(json.toString(2))
            Log.d(TAG, "Session saved: ${session.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session", e)
        }
    }

    suspend fun loadSession(sessionId: String): Session? = withContext(Dispatchers.IO) {
        try {
            val file = File(sessionsDir, "$sessionId.json")
            if (!file.exists()) return@withContext null
            val json = JSONObject(file.readText())
            jsonToSession(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load session: $sessionId", e)
            null
        }
    }

    suspend fun getAllSessions(): List<Session> = withContext(Dispatchers.IO) {
        try {
            sessionsDir.listFiles { file -> file.extension == "json" }
                ?.mapNotNull { file ->
                    try {
                        val json = JSONObject(file.readText())
                        jsonToSession(json)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse session file: ${file.name}", e)
                        null
                    }
                }
                ?.sortedByDescending { it.updatedAt }
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all sessions", e)
            emptyList()
        }
    }

    suspend fun deleteSession(sessionId: String) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(sessionsDir, "$sessionId.json")
                if (file.exists()) {
                    file.delete()
                    Log.d(TAG, "Session deleted: $sessionId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete session: $sessionId", e)
            }
            Unit
        }
    }

    private fun sessionToJson(session: Session): JSONObject {
        return JSONObject().apply {
            put("id", session.id)
            put("title", session.title)
            put("createdAt", session.createdAt)
            put("updatedAt", session.updatedAt)

            val messagesArray = JSONArray()
            session.messages.forEach { message ->
                messagesArray.put(JSONObject().apply {
                    put("id", message.id)
                    put("sender", message.sender.name)
                    put("content", message.content)
                    put("speakerName", message.speakerName ?: JSONObject.NULL)
                    put("timestamp", message.timestamp)
                })
            }
            put("messages", messagesArray)

            val aliasesObject = JSONObject()
            session.speakerAliases.forEach { (key, value) ->
                aliasesObject.put(key, value)
            }
            put("speakerAliases", aliasesObject)
        }
    }

    private fun jsonToSession(json: JSONObject): Session {
        val messages = mutableListOf<Message>()
        val messagesArray = json.optJSONArray("messages") ?: JSONArray()
        for (i in 0 until messagesArray.length()) {
            val msgJson = messagesArray.getJSONObject(i)
            messages.add(Message(
                id = msgJson.getString("id"),
                sender = Sender.valueOf(msgJson.getString("sender")),
                content = msgJson.getString("content"),
                speakerName = if (msgJson.isNull("speakerName")) null else msgJson.getString("speakerName"),
                timestamp = msgJson.optLong("timestamp", System.currentTimeMillis())
            ))
        }

        val speakerAliases = mutableMapOf<String, String>()
        val aliasesObject = json.optJSONObject("speakerAliases") ?: JSONObject()
        aliasesObject.keys().forEach { key ->
            speakerAliases[key] = aliasesObject.getString(key)
        }

        return Session(
            id = json.getString("id"),
            title = json.optString("title", ""),
            createdAt = json.getLong("createdAt"),
            updatedAt = json.getLong("updatedAt"),
            messages = messages,
            speakerAliases = speakerAliases
        )
    }
}
