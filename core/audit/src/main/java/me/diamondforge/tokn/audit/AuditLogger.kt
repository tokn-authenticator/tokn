package me.diamondforge.tokn.audit

interface AuditLogger {
    fun log(type: AuditEventType, targetId: Long? = null, detail: String? = null)
    fun setEnabled(enabled: Boolean)
    fun setDisabledCategories(categories: Set<AuditCategory>)
}

object NoopAuditLogger : AuditLogger {
    override fun log(type: AuditEventType, targetId: Long?, detail: String?) = Unit
    override fun setEnabled(enabled: Boolean) = Unit
    override fun setDisabledCategories(categories: Set<AuditCategory>) = Unit
}
