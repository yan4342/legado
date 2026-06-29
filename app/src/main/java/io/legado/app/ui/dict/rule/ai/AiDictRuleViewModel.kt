package io.legado.app.ui.dict.rule.ai

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.AiDictRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AiDictRuleViewModel(application: Application) : BaseViewModel(application) {

    private val _rulesFlow = MutableStateFlow<List<AiDictRule>>(emptyList())
    val rulesFlow: StateFlow<List<AiDictRule>> = _rulesFlow.asStateFlow()

    init {
        execute {
            _rulesFlow.value = appDb.aiDictRuleDao.all
        }
    }

    fun toggleEnabled(rule: AiDictRule) {
        _rulesFlow.value = _rulesFlow.value.map { item ->
            if (item.name == rule.name) item.copy(enabled = !item.enabled) else item
        }
        execute {
            val updated = rule.copy(enabled = !rule.enabled)
            appDb.aiDictRuleDao.update(updated)
        }
    }

    fun delete(name: String) {
        _rulesFlow.update { rules -> rules.filter { it.name != name } }
        execute {
            val rule = appDb.aiDictRuleDao.getByName(name)
            if (rule != null) {
                appDb.aiDictRuleDao.delete(rule)
            }
        }
    }
}
