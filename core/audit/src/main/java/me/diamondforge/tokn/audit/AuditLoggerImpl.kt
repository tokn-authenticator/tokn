package me.diamondforge.tokn.audit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLoggerImpl(
    private val dao: AuditLogDao,
    private val scope: CoroutineScope,
) : AuditLogger {

    @Inject
    constructor(dao: AuditLogDao) : this(dao, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private val enabled = MutableStateFlow(true)
    private val disabledCategories = MutableStateFlow<Set<AuditCategory>>(emptySet())

    override fun log(type: AuditEventType, targetId: Long?, detail: String?) {
        scope.launch {
            if (!enabled.value) return@launch
            if (type.category in disabledCategories.value) return@launch
            runCatching {
                dao.insert(
                    AuditLogEntry(
                        type = type.name,
                        category = type.category.name,
                        timestamp = System.currentTimeMillis(),
                        targetId = targetId,
                        detail = detail,
                    ),
                )
            }
        }
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled.value = enabled
    }

    override fun setDisabledCategories(categories: Set<AuditCategory>) {
        disabledCategories.value = categories
    }
}
