package com.sclera.sclerago.domain.validation

/** The longest comment the server keeps (its `COMMENT_MAX_LENGTH`). */
const val COMMENT_MAX_LENGTH = 5000

/** Why a comment can't be posted as typed. */
enum class CommentProblem { EMPTY, TOO_LONG }

/** What is posted for [text]: trimmed, as the server stores it. */
fun commentText(text: String): String = text.trim()

/** The problem with posting [text] (null when it can be posted): blank, or longer than [COMMENT_MAX_LENGTH] once trimmed. */
fun checkComment(text: String): CommentProblem? {
    val trimmed = commentText(text)
    return when {
        trimmed.isEmpty() -> CommentProblem.EMPTY
        trimmed.length > COMMENT_MAX_LENGTH -> CommentProblem.TOO_LONG
        else -> null
    }
}
