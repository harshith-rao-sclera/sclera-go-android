package com.sclera.sclerago.domain.repository

/** Something that holds the signed-in user's data and can throw it all away (on sign-out or when a session ends). */
interface UserDataCleaner {
    /** Forgets everything this keeps about the user and goes back to its starting state. */
    fun clear()
}
