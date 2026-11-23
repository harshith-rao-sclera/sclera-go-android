package com.sclera.sclerago.domain.model

// Configuration's spreadsheet imports (Building info → Import buildings, Asset fields → Import from Excel), read and
// checked at the server as the web's: columns are matched by heading, every row is checked first and a sheet with a
// mistake makes nothing.

/** Why the server left a row of the sheet out. */
enum class SkipReason {
    /** The property already has it (a location on that floor, or a field of that name). */
    ALREADY_THERE,

    /** A photo, video or voice memo row: those go in the asset's Media, not in a field. */
    MEDIA,
}

/** A row of the sheet that was left out: its [row] as Excel numbers it, what it names and why. */
data class SheetSkip(val row: Int, val name: String, val reason: SkipReason)

/**
 * What Import buildings made: how many [buildings], [floors] and [locations] were new, the rows [skipped] (locations
 * already there) and the sheet's [ignoredColumns] (headings that aren't a building, floor or location detail).
 */
data class PlacesImported(
    val buildings: Int,
    val floors: Int,
    val locations: Int,
    val skipped: List<SheetSkip>,
    val ignoredColumns: List<String>,
)

/**
 * What Import from Excel read (nothing is made yet): the new [fields] to review, the rows [skipped] (names already
 * there, photo / video / voice memo rows) and the sheet's [ignoredColumns].
 */
data class FieldsRead(val fields: List<FieldCfg>, val skipped: List<SheetSkip>, val ignoredColumns: List<String>)

/**
 * The server refused the whole sheet, so nothing was imported. [rows] are the rows to fix (as Excel numbers them); empty
 * when the sheet as a whole is wrong (a required column missing, no rows, too many rows, not an .xlsx).
 */
class SheetRejectedException(val rows: List<Int>) : Exception("sheet rejected")
