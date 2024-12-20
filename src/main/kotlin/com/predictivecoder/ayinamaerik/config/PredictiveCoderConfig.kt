package com.predictivecoder.ayinamaerik.config

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.xmlb.XmlSerializerUtil
import java.io.File
import java.util.*

@State(
    name = "PredictiveCoderConfig",
    storages = [Storage("predictiveCoder.xml")]
)
@Service(Service.Level.APP)
class PredictiveCoderConfig : PersistentStateComponent<PredictiveCoderConfig> {
    private val logger = Logger.getInstance(PredictiveCoderConfig::class.java)

    var apiKey: String = ""

    init {
        loadApiKey()
    }

    private fun loadApiKey() {
        apiKey = findApiKey()
    }

    private fun findApiKey(): String {
        return sequence {
            // 1. Chercher dans les propriétés du système
            yield(System.getProperty("openai.api.key"))

            // 2. Chercher dans les variables d'environnement
            yield(System.getenv("OPENAI_API_KEY"))

            // 3. Chercher dans le fichier .env à la racine du projet
            yield(loadFromEnvFile(".env"))

            // 4. Chercher dans le fichier de configuration utilisateur
            yield(loadFromEnvFile(System.getProperty("user.home") + "/.predictivecoder/config"))

            // 5. Chercher dans les ressources du plugin
            yield(loadFromResources("config.properties"))
        }.filterNotNull().firstOrNull() ?: ""
    }

    private fun loadFromEnvFile(path: String): String? {
        return try {
            File(path).takeIf { it.exists() }?.useLines { lines ->
                lines.find { it.startsWith("OPENAI_API_KEY=") }
                    ?.split("=", limit = 2)
                    ?.getOrNull(1)
                    ?.trim()
            }
        } catch (e: Exception) {
            logger.debug("Failed to read from $path: ${e.message}")
            null
        }
    }

    private fun loadFromResources(resourceName: String): String? {
        return try {
            val properties = Properties()
            javaClass.classLoader.getResourceAsStream(resourceName)?.use {
                properties.load(it)
            }
            properties.getProperty("OPENAI_API_KEY")
        } catch (e: Exception) {
            logger.debug("Failed to read from resources: ${e.message}")
            null
        }
    }

    override fun getState(): PredictiveCoderConfig = this

    override fun loadState(state: PredictiveCoderConfig) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun isConfigured(): Boolean = apiKey.isNotBlank()

    companion object {
        fun getInstance(): PredictiveCoderConfig =
            service<PredictiveCoderConfig>()
    }
}