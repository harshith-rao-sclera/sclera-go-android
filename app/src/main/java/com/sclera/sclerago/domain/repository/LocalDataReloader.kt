package com.sclera.sclerago.domain.repository

/** Reads the open property's data on this device again, so every list shows what is stored now. */
interface LocalDataReloader {
    fun reload()
}
