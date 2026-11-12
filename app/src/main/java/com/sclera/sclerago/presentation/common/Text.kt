package com.sclera.sclerago.presentation.common

/** "1 asset" / "3 assets". */
fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
