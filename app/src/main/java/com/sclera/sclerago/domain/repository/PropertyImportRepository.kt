package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.ImportResult
import com.sclera.sclerago.domain.model.ImportSuggestion
import com.sclera.sclerago.domain.model.ImportTarget

/**
 * Importing properties from a spreadsheet at the server ([file] is the picked file's content URI). Online only, as on
 * the web; every call can throw OnlineException.
 */
interface PropertyImportRepository {
    /** Saves the server's sample sheet to [destination] (a document URI the user chose). */
    suspend fun saveTemplate(destination: String)

    /** The property details a column can be imported into. */
    suspend fun targets(): List<ImportTarget>

    /** Which detail each of [file]'s columns most likely holds (nothing is saved). */
    suspend fun suggest(file: String): ImportSuggestion

    /** Imports [file]'s rows as properties, reading each column as [mapping] says (column heading → detail key). */
    suspend fun import(file: String, mapping: Map<String, String>): ImportResult
}
