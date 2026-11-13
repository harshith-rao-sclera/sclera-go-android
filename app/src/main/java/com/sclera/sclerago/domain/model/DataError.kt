package com.sclera.sclerago.domain.model

/** Why a change to the offline data could not be made. */
enum class DataError {
    /** The offline data is not downloaded (or was cleared by a sign-out). */
    NOT_READY,

    /** Something with that name is already there. */
    NAME_TAKEN,

    /** Still used by something else (a building with floors, a location with assets). */
    IN_USE,

    /** Anything else; the details stay out of the message. */
    FAILED,
}

/** A change to the offline data was refused or failed; [error] says why, in terms the screens can show. */
class DataException(val error: DataError, cause: Throwable? = null) : Exception(error.name, cause)
