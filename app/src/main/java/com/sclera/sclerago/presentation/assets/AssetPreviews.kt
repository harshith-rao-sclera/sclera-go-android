package com.sclera.sclerago.presentation.assets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.sclera.sclerago.presentation.preview.SAMPLE_FIELDS
import com.sclera.sclerago.presentation.preview.SAMPLE_DETAILS
import com.sclera.sclerago.presentation.preview.SAMPLE_ASSETS
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.usecase.asset.BuildFormValuesUseCase
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.main.ReviewItem
import com.sclera.sclerago.presentation.theme.ScleraTheme

private const val PHONE = "spec:width=390dp,height=844dp"
private const val TABLET_PORTRAIT = "spec:width=800dp,height=1280dp,dpi=240"
private const val TABLET_LANDSCAPE = "spec:width=1280dp,height=800dp,dpi=240"

/** Fixed time the sample "Updated" line shows: 5 Nov 2026, 10:42 UTC. */
internal const val SampleUpdatedAt = 1_793_875_320_000L

internal val SamplePhotos = listOf(
    Media("m1", "content://sample/1", video = false, createdAt = SampleUpdatedAt),
    Media("m2", "content://sample/2", video = true, durationMs = 12_000, createdAt = SampleUpdatedAt),
)

/**
 * The details of seed asset [id] as the screen shows them (the Pump from the design by default): the form
 * values and any [edits] typed on top.
 */
internal fun sampleDetailState(
    id: String = "a2",
    edits: Map<String, String> = emptyMap(),
    media: List<Media> = SamplePhotos,
    status: Status? = null,
    isNew: Boolean = false,
): AssetDetailState {
    val seed = SAMPLE_ASSETS.first { it.id == id }.copy(updatedAt = SampleUpdatedAt, media = media)
    val asset = status?.let { seed.copy(st = it) } ?: seed
    val details = if (isNew) null else SAMPLE_DETAILS[id]
    val saved = BuildFormValuesUseCase { 0L }(asset.name, isNew, details, SAMPLE_FIELDS, emptyMap())
    return AssetDetailState(
        asset = asset, isNew = isNew, saved = saved, edits = edits, fields = SAMPLE_FIELDS,
        details = details, media = media, newPicks = if (isNew) media else emptyList(),
    )
}

internal val SampleReviewItems = listOf(
    ReviewItem("a", "Asset from photo 14:02:31", UiText.Res(com.sclera.sclerago.R.string.review_photo_sub), ready = false, on = true),
    ReviewItem("b", "Asset from video 14:03:10", UiText.Res(com.sclera.sclerago.R.string.review_video_sub, listOf("0:12")), ready = false, on = false),
    ReviewItem("c", "Pump list", UiText.Raw("pumps.xlsx · 24 KB"), ready = true, on = true, ext = "XLSX", extColor = Color(0xFF4CC487)),
)

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun DetailInfoPreview() = ScleraTheme { AssetDetailScreen(sampleDetailState(), AssetDetailActions()) }

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun DetailNewPreview() = ScleraTheme { AssetDetailScreen(sampleDetailState(isNew = true, media = emptyList()), AssetDetailActions()) }

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun DetailSubmittedPreview() = ScleraTheme { AssetDetailScreen(sampleDetailState(id = "a4"), AssetDetailActions()) }

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun ReviewPreview() = ScleraTheme {
    ReviewScreen("3 items · each becomes a draft asset", "Add more", {}, SampleReviewItems, {}, {}, {}) { _, _ -> }
}
