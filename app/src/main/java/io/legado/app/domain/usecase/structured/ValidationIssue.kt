package io.legado.app.domain.usecase.structured

sealed class ValidationIssue {
    data class Error(val message: String, val suggestion: String? = null) : ValidationIssue()
    data class Warning(val message: String) : ValidationIssue()
}

/** Message + optional suggestion for UI / model rejection feedback. */
fun ValidationIssue.Error.formattedFeedback(): String =
    if (suggestion.isNullOrBlank()) message else "$message — $suggestion"

data class ValidationResult(val issues: List<ValidationIssue>) {
    val blocking: ValidationIssue.Error? get() = issues.filterIsInstance<ValidationIssue.Error>().firstOrNull()
    val warnings: List<ValidationIssue.Warning> get() = issues.filterIsInstance<ValidationIssue.Warning>()
}
