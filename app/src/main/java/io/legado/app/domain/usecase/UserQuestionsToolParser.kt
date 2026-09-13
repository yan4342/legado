package io.legado.app.domain.usecase

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.utils.GSON

data class UserQuestionOption(
    val id: String,
    val label: String,
)

data class UserQuestion(
    val id: String,
    val prompt: String,
    val options: List<UserQuestionOption>,
    val allowMultiple: Boolean,
)

object UserQuestionsToolParser {

    private const val MAX_QUESTIONS = 3
    private const val MIN_OPTIONS = 2
    private const val MAX_OPTIONS = 6

    fun parseQuestionsJson(raw: String?): ParseResult {
        if (raw.isNullOrBlank()) {
            return ParseResult.Error("questions array is required")
        }
        val array = runCatching {
            JsonParser.parseString(raw).asJsonArray
        }.getOrElse {
            return ParseResult.Error("Invalid questions JSON")
        }
        if (array.size() == 0) {
            return ParseResult.Error("At least one question is required")
        }
        if (array.size() > MAX_QUESTIONS) {
            return ParseResult.Error("At most $MAX_QUESTIONS questions allowed")
        }
        val questions = mutableListOf<UserQuestion>()
        for (i in 0 until array.size()) {
            val item = array.get(i)
            if (!item.isJsonObject) {
                return ParseResult.Error("Question $i must be an object")
            }
            val question = parseQuestion(item.asJsonObject)
                ?: return ParseResult.Error("Invalid question at index $i")
            questions.add(question)
        }
        return ParseResult.Success(questions)
    }

    fun parseQuestionsFromArgs(args: JsonObject): ParseResult {
        val raw = args.get("questions")?.takeIf { !it.isJsonNull }?.let { element ->
            when {
                element.isJsonArray -> element.asJsonArray.toString()
                element.isJsonPrimitive -> element.asString
                else -> null
            }
        }
        return parseQuestionsJson(raw)
    }

    fun formatAnswersJson(answers: List<UserQuestionAnswer>): String {
        return GSON.toJson(mapOf("answers" to answers.map { answer ->
            mapOf(
                "questionId" to answer.questionId,
                "selectedOptionIds" to answer.selectedOptionIds,
                "customText" to answer.customText,
            )
        }))
    }

    private fun parseQuestion(obj: JsonObject): UserQuestion? {
        val id = obj.string("id")?.trim().orEmpty()
        val prompt = obj.string("prompt")?.trim().orEmpty()
        if (id.isBlank() || prompt.isBlank()) {
            return null
        }
        val optionsElement = obj.get("options")
        if (optionsElement == null || !optionsElement.isJsonArray) {
            return null
        }
        val optionsArray = optionsElement.asJsonArray
        if (optionsArray.size() < MIN_OPTIONS || optionsArray.size() > MAX_OPTIONS) {
            return null
        }
        val options = buildList {
            for (j in 0 until optionsArray.size()) {
                val opt = optionsArray.get(j)
                if (!opt.isJsonObject) return null
                val optObj = opt.asJsonObject
                val optId = optObj.string("id")?.trim().orEmpty()
                val label = optObj.string("label")?.trim().orEmpty()
                if (optId.isBlank() || label.isBlank()) return null
                add(UserQuestionOption(optId, label))
            }
        }
        val allowMultiple = obj.get("allow_multiple")?.takeIf { !it.isJsonNull }?.asBoolean == true
            || obj.get("allowMultiple")?.takeIf { !it.isJsonNull }?.asBoolean == true
        return UserQuestion(id, prompt, options, allowMultiple)
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { !it.isJsonNull }?.asString

    sealed class ParseResult {
        data class Success(val questions: List<UserQuestion>) : ParseResult()
        data class Error(val message: String) : ParseResult()
    }
}

data class UserQuestionAnswer(
    val questionId: String,
    val selectedOptionIds: List<String>,
    val customText: String?,
)
