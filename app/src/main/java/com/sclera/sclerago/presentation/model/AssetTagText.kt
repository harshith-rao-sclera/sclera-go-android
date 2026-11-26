package com.sclera.sclerago.presentation.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AssetKind
import com.sclera.sclerago.domain.model.LocationTag
import com.sclera.sclerago.domain.model.TagSpot
import com.sclera.sclerago.domain.model.TagSurface

// How an asset's Type and Location tag read on screen (the asset form and the validation review).

/** A location tag as the form shows it: "Wall : North". */
@Composable
internal fun locationTagText(tag: LocationTag): String =
    stringResource(R.string.location_tag_value, stringResource(tag.surface.labelRes), stringResource(tag.spot.labelRes))

internal val AssetKind.labelRes: Int
    get() = when (this) {
        AssetKind.EXISTING -> R.string.asset_kind_existing
        AssetKind.PROPOSED -> R.string.asset_kind_proposed
    }

internal val TagSurface.labelRes: Int
    get() = when (this) {
        TagSurface.CEILING -> R.string.location_tag_surface_ceiling
        TagSurface.WALL -> R.string.location_tag_surface_wall
        TagSurface.FLOOR -> R.string.location_tag_surface_floor
    }

internal val TagSpot.labelRes: Int
    get() = when (this) {
        TagSpot.BELOW -> R.string.location_tag_spot_below
        TagSpot.ABOVE -> R.string.location_tag_spot_above
        TagSpot.NORTH -> R.string.location_tag_spot_north
        TagSpot.SOUTH -> R.string.location_tag_spot_south
        TagSpot.EAST -> R.string.location_tag_spot_east
        TagSpot.WEST -> R.string.location_tag_spot_west
        TagSpot.ON -> R.string.location_tag_spot_on
        TagSpot.NONE -> R.string.location_tag_spot_none
    }
