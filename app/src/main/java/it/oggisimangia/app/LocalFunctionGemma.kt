package it.oggisimangia.app

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Experimental FunctionGemma inference strictly on-device. No cloud calls, model
 * download, source data extraction, or direct database writes are performed here.
 * The registered tool merely returns a proposed command which is revalidated by
 * VoiceFlowPlanner and executed later by the normal user-facing Android flow.
 */
class LocalFunctionGemma(private val context: Context) {
    companion object {
        const val PICK_MODEL_REQUEST = 521
        private const val MODEL_NAME = "functiongemma.litertlm"
        private const val SETTINGS = "functiongemma_local_v1"
        private const val KEY_ENABLED = "enabled"
        private const val MIN_MODEL_BYTES = 20L * 1024L * 1024L
        private const val MAX_MODEL_BYTES = 1024L * 1024L * 1024L
    }

    enum class State { NEEDS_MODEL, DISABLED, READY, BUSY, LOW_MEMORY, ERROR }
    data class Diagnostics(
        val manufacturer: String,
        val model: String,
        val physicalRamMb: Long,
        val availableRamMb: Long,
        val availableStorageMb: Long,
        val arm64: Boolean,
        val modelMb: Long,
        val state: State,
        val lastInferenceMs: Long?
    ) {
        val message: String get() =
            "Telefono: $manufacturer $model\n" +
            "RAM fisica: $physicalRamMb MB; disponibile: $availableRamMb MB.\n" +
            "Spazio interno libero: $availableStorageMb MB; ARM64: ${if (arm64) "sì" else "no"}.\n" +
            "Modello locale: $modelMb MB. Stato: $state.\n" +
            "Ultima interpretazione: ${lastInferenceMs?.let { "${it} ms" } ?: "non ancora misurata"}."
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
    private val busy = AtomicBoolean(false)
    private val directory = File(context.filesDir, "local-ai")
    private val modelFile = File(directory, MODEL_NAME)
    private var lastFailure: String = ""
    private var latencyMs: Long? = null
    @Volatile private var importing = false

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_ENABLED, value).apply() }

    fun diagnostics(): Diagnostics {
        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val storage = StatFs(context.filesDir.absolutePath)
        return Diagnostics(
            Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty(),
            memory.totalMem / 1048576, memory.availMem / 1048576,
            storage.availableBytes / 1048576,
            Build.SUPPORTED_64_BIT_ABIS.any { it == "arm64-v8a" },
            if (modelFile.isFile) modelFile.length() / 1048576 else 0L,
            status(memory), latencyMs
        )
    }

    private fun status(memory: ActivityManager.MemoryInfo? = null): State {
        if (!enabled) return State.DISABLED
        if (busy.get() || importing) return State.BUSY
        if (!modelFile.isFile || modelFile.length() < MIN_MODEL_BYTES) return State.NEEDS_MODEL
        val m = memory ?: ActivityManager.MemoryInfo().also {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
        }
        if (m.availMem < 350L * 1048576L || Build.SUPPORTED_64_BIT_ABIS.none { it == "arm64-v8a" }) return State.LOW_MEMORY
        return State.READY
    }

    fun failureMessage(): String = lastFailure
    fun modelPresent() = modelFile.exists() && modelFile.length() >= MIN_MODEL_BYTES

    /** Import once through the Android system picker, never fetching a model silently. */
    fun importModel(uri: Uri, onDone: (Boolean, String) -> Unit) {
        if (!busy.compareAndSet(false, true)) {
            onDone(false, "Un'operazione AI è già in corso.")
            return
        }
        importing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val temporary = File(directory, "$MODEL_NAME.pending")
                try {
                    directory.mkdirs()
                    val length = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                    require(length == -1L || length in MIN_MODEL_BYTES..MAX_MODEL_BYTES) {
                        "Il file non sembra un modello FunctionGemma compatibile."
                    }
                    var bytes = 0L
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temporary.outputStream().use { output ->
                            val block = ByteArray(128 * 1024)
                            while (true) {
                                val count = input.read(block)
                                if (count < 0) break
                                bytes += count
                                require(bytes <= MAX_MODEL_BYTES) { "Modello troppo grande." }
                                output.write(block, 0, count)
                            }
                        }
                    } ?: error("Impossibile aprire il file.")
                    require(bytes >= MIN_MODEL_BYTES) { "Modello incompleto." }
                    require(temporary.renameTo(modelFile)) { "Impossibile finalizzare il modello." }
                    true to "Modello copiato sul telefono (${bytes / 1048576} MB). Pronto per la prova AI."
                } catch (error: Exception) {
                    temporary.delete()
                    false to (error.message ?: "Importazione non riuscita.")
                }
            }
            importing = false
            busy.set(false)
            onDone(result.first, result.second)
        }
    }

    private class Proposals : OpenApiTool {
        val collected = mutableListOf<String>()
        override fun getToolDescriptionJsonString(): String = """
            {
              "name": "propose_app_commands",
              "description": "Translate an Italian user's cooking app goal to supported canonical Italian commands. No execution. Use exactly one call. If ambiguous, ask the user instead.",
              "parameters": {
                "type": "object",
                "properties": {
                  "commands": {
                    "type": "string",
                    "description": "One or more canonical commands joined by 'e poi', maximum six. Examples: Apri il calendario; Cosa mangio domani; Cerca ricette con pasta e zucchine; Pianifica Risotto per cena domani."
                  }
                },
                "required": ["commands"]
              }
            }
        """.trimIndent()

        override fun execute(paramsJsonString: String): String {
            if (collected.isNotEmpty()) return """{"accepted":false,"reason":"one call only"}"""
            val proposed = try { JSONObject(paramsJsonString).optString("commands").trim() }
                catch (_: Exception) { "" }
            val plan = VoiceFlowPlanner.build(proposed)
            if (proposed.isBlank() || proposed.length > 650 || plan.clarification != null ||
                plan.conflict != null || plan.commands.isEmpty() || plan.commands.size > 6) {
                return """{"accepted":false,"reason":"ambiguous or unsupported command"}"""
            }
            collected.add(proposed)
            return """{"accepted":true,"note":"proposed but not executed"}"""
        }
    }

    /** Runs CPU inference in a worker, validates commands, and falls back to Nano/parser. */
    fun interpret(phrase: String, onDone: (String?) -> Unit) {
        if (phrase.isBlank() || status() != State.READY || !busy.compareAndSet(false, true)) {
            onDone(null)
            return
        }
        scope.launch {
            val answer = withContext(Dispatchers.IO) {
                val start = SystemClock.elapsedRealtime()
                try {
                    val tools = Proposals()
                    // CPU backend: avoids vendor-specific NPU/GPU assumptions on Redmi/A17.
                    Engine(EngineConfig(modelPath = modelFile.absolutePath, backend = Backend.CPU())).use { engine ->
                        engine.initialize()
                        val config = ConversationConfig(
                            tools = listOf(tool(tools)),
                            automaticToolCalling = true
                        )
                        engine.createConversation(config).use { conversation ->
                            conversation.sendMessage(
                                "Interpreta la richiesta in italiano usando solo propose_app_commands. " +
                                "Non inventare ingredienti, date o nomi di ricette. " +
                                "Non cancellare mai nulla. Se mancano dati, non chiamare strumenti. " +
                                "Richiesta: " + phrase.take(480)
                            )
                        }
                    }
                    lastFailure = ""
                    tools.collected.singleOrNull()?.takeIf {
                        VoiceFlowPlanner.build(it).let { plan ->
                            plan.commands.isNotEmpty() && plan.clarification == null && plan.conflict == null
                        }
                    }
                } catch (ex: Exception) {
                    lastFailure = (ex.message ?: ex.javaClass.simpleName).take(200)
                    null
                } catch (err: LinkageError) {
                    lastFailure = ("Runtime AI non compatibile: " + err.javaClass.simpleName).take(200)
                    null
                } finally {
                    latencyMs = SystemClock.elapsedRealtime() - start
                }
            }
            busy.set(false)
            onDone(answer)
        }
    }

    fun close() { scope.cancel() }
}
