package com.example.sclerago.services.syncManager.enums;

public enum OfflineFeatures {
    ADMIN,
    ASSET_DATA_COLLECTION;

    public static OfflineFeatures fromString(String name) {
        if (name == null) return null;
        switch (name.toLowerCase()) {
            case "admin":
                return ADMIN;
            case "asset-data-collection":
                return ASSET_DATA_COLLECTION;
            default:
                return null;
        }
    }
}
