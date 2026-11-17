package com.sclera.sclerago.domain.validation

/** How an asset came out of its latest validation result. [LOOK] = worth a look, not a hard failure. */
enum class RuleOutcome { PASS, LOOK, FAIL, NA }

/** One step's outcome for an asset, with the first problem found in it. */
data class Finding(val rule: String, val outcome: RuleOutcome, val message: String?)

/** An asset's latest validation result (the asset list shows it): its outcome and its steps' findings. */
data class AssetResult(val assetId: String, val assetName: String, val outcome: RuleOutcome, val findings: List<Finding>)
