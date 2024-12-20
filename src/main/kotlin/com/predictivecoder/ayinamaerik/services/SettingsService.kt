package com.predictivecoder.ayinamaerik.services

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(
    name = "PredictiveCoderSettings",
    storages = [Storage("predictiveCoder.xml")]
)
class SettingsService : PersistentStateComponent<SettingsService> {
    var isEnabled: Boolean = true
    var autoSuggestEnabled: Boolean = true
    var suggestionDelay: Int = 500 // milliseconds
    var minCharsForSuggestion: Int = 3
    var maxContextLength: Int = 1000

    override fun getState(): SettingsService = this

    override fun loadState(state: SettingsService) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): SettingsService = service()
    }
}