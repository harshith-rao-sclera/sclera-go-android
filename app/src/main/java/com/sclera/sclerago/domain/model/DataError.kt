package com.sclera.sclerago.domain.model

/** Why a change to the offline data could not be made. */
enum class DataError {
    /** The offline data is not downloaded (or was cleared by a sign-out). */
    NOT_READY,

    /** Something with that name is already there. */
    NAME_TAKEN,

    /** Still used by something else (a building with floors, a location with assets). */
    IN_USE,

    /** The signed-in user's group does not allow it (what the server last said; the server still decides). */
    FORBIDDEN,

    /** Someone who isn't an admin changes or submits only the assets assigned to them (the server's check_editable). */
    NOT_ASSIGNED,

    /** The asset is in Managed Assets: read-only here for everyone, admins too (the server answers 404 to any change). */
    MANAGED,

    /** A photo or file is bigger than the 5MB an asset keeps (and couldn't be made smaller). */
    FILE_TOO_LARGE,

    /** A video is longer than the 10 seconds an asset keeps (and couldn't be cut down). */
    CLIP_TOO_LONG,

    /** One more photo of a kind than the property's photo rules allow (or a kind it doesn't collect). */
    PHOTO_LIMIT,

    /** Anything else; the details stay out of the message. */
    FAILED,
}

/** A change to the offline data was refused or failed; [error] says why, in terms the screens can show. */
class DataException(val error: DataError, cause: Throwable? = null) : Exception(error.name, cause)
