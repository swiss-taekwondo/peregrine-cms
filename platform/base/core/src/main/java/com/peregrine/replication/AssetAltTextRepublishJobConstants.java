package com.peregrine.replication;

/** Shared job identifiers for publishing pages affected by an image alt-text change. */
public final class AssetAltTextRepublishJobConstants {
    public static final String TOPIC = "com/peregrine/replication/REPUBLISH_ASSET_ALT_TEXT_REFERENCES";
    public static final String ASSET_PATHS = "assetPaths";
    public static final String REPORT_TOKEN = "reportToken";

    private AssetAltTextRepublishJobConstants() {
        // Utility class.
    }
}
