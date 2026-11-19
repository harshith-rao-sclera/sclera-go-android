package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.mapper.directionJson
import com.sclera.sclerago.data.remote.tagging.TagApi
import com.sclera.sclerago.data.remote.tagging.TagPaths
import com.sclera.sclerago.data.remote.tagging.registrationKeys
import com.sclera.sclerago.data.sync.SyncCredentialsProvider
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.TagCheck
import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.domain.model.TagUpdate
import com.sclera.sclerago.domain.model.TagValue
import com.sclera.sclerago.domain.model.TagWrite
import com.sclera.sclerago.domain.model.TaggingError
import com.sclera.sclerago.domain.model.TaggingException
import com.sclera.sclerago.domain.repository.TaggingRepository
import android.util.Log
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/** Checks, registers and writes tags through the Sclera server that holds the physical tags; online only. */
class TaggingRepositoryImpl @Inject constructor(
    private val api: TagApi,
    private val credentials: SyncCredentialsProvider,
) : TaggingRepository {
    override suspend fun check(tag: TagValue, asset: String, property: String): TagCheck {
        val answer = api.check(TagPaths.check(tag), asset, property)
        // Debug builds only (R8 removes Log.d): what the server answered, as flags and counts. Never an id or a tag's content.
        Log.d(LOG, "check ${tag.kind} system=${tag.isSystem} sentProperty=${property.isNotEmpty()} isTagged=${answer?.isTagged} isAdc=${answer?.isAdc} " +
            "hasHolder=${!answer?.collectionId.isNullOrEmpty()} sameAsAsset=${answer?.collectionId == asset} count=${answer?.existingCollectionIdCount} " +
            "otherProperty=${answer?.isTaggedToProperty}")
        return answer?.toDomain() ?: throw TaggingException(TaggingError.INVALID_RESPONSE)
    }

    override suspend fun register(tag: TagValue, asset: String, property: String) {
        val me = credentials.credentials.value
        val (idKey, userKey) = registrationKeys(tag.kind)
        val body = buildJsonObject {
            put(idKey, tag.id)
            put(userKey, me?.email.orEmpty())
            put("orgId", me?.organisationId.orEmpty())
            put("deviceId", asset)
            put("propertyId", property)
        }
        try {
            api.register(TagPaths.register(tag), body)
        } catch (e: OnlineException) {
            // As the old app: anything but being offline is "failed to register"; a refusal here is not the user's session ending.
            if (e.error == OnlineError.Offline) throw e
            throw TaggingException(TaggingError.REGISTRATION_FAILED)
        }
    }

    override suspend fun write(write: TagWrite): TagUpdate {
        val isQr = write.tag.kind == TagKind.QR
        val body = buildJsonObject {
            put("updatedBy", credentials.credentials.value?.email.orEmpty())
            put("deviceId", write.asset)
            put("existingCollectionId", write.previousAsset.orEmpty())
            put("movable", if (isQr) (if (write.placement.isMovable) "true" else "false") else "")
            put("direction", if (isQr) write.placement.direction?.let(::directionJson).orEmpty() else "")
            put("propertyId", write.property)
        }
        val answer = api.write(TagPaths.update(write.tag), body, if (isQr) write.photo?.data else null)
        Log.d(LOG, "write ${write.tag.kind} status=${answer.status} new=${answer.data?.newCollectionIdCount} previous=${answer.data?.existingCollectionIdCount} moved=${write.previousAsset != null}")
        if (answer.status == CLAIMED_ELSEWHERE) throw TaggingException(TaggingError.CLAIMED_ELSEWHERE)
        return TagUpdate(answer.data?.newCollectionIdCount ?: 0, answer.data?.existingCollectionIdCount ?: 0)
    }

    private companion object {
        const val LOG = "Tagging"
        /** The server's own code for "someone else got this tag while the change was waiting". */
        const val CLAIMED_ELSEWHERE = 826
    }
}
