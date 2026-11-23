package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.AssetActivity
import com.sclera.sclerago.domain.model.DataError
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.repository.AssetActivityRepository
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.validation.CommentProblem
import com.sclera.sclerago.domain.validation.checkComment
import com.sclera.sclerago.domain.validation.commentText
import javax.inject.Inject

/**
 * Changes a comment's text, as the server's `PATCH …/comments/{id}`: only its author may ([AssetActivity.canEdit], else
 * [DataException] FORBIDDEN), not on an asset in Managed Assets (MANAGED, the server answers 404), and the text must
 * pass [checkComment]. Returns the problem with [text] and changes nothing, or null once it is saved.
 */
class UpdateCommentUseCase @Inject constructor(private val repo: AssetActivityRepository, private val assets: AssetRepository) {
    suspend operator fun invoke(comment: AssetActivity, text: String): CommentProblem? {
        if (!comment.canEdit) throw DataException(DataError.FORBIDDEN)
        requireNotManaged(assets, listOf(comment.assetId))
        checkComment(text)?.let { return it }
        repo.updateComment(comment.assetId, comment.id, commentText(text))
        return null
    }
}

/**
 * Removes a comment, as the server's `DELETE …/comments/{id}`: its author, or anyone who can validate
 * ([AssetActivity.canDelete], else [DataException] FORBIDDEN); not on an asset in Managed Assets (MANAGED, the server
 * answers 404). It is marked removed, never deleted.
 */
class RemoveCommentUseCase @Inject constructor(private val repo: AssetActivityRepository, private val assets: AssetRepository) {
    suspend operator fun invoke(comment: AssetActivity) {
        if (!comment.canDelete) throw DataException(DataError.FORBIDDEN)
        requireNotManaged(assets, listOf(comment.assetId))
        repo.removeComment(comment.assetId, comment.id)
    }
}
