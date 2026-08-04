package com.callbloqued.sift.domain.model

/**
 * Identifies whether a manual list entry belongs to the user's blacklist or whitelist.
 *
 * Stored by name in the database ([com.callbloqued.sift.data.local.db.ManualListEntity.listType])
 * so that reordering enum entries in future phases does not corrupt existing records.
 */
enum class ManualListType {

    /**
     * Numbers the user explicitly wants to block, regardless of the attempt threshold.
     * A blacklisted number is always silently rejected unless it is also present in
     * the device contacts (non-negotiable rule: known contacts are never blocked).
     */
    BLACKLIST,

    /**
     * Numbers the user explicitly wants to allow through screening, bypassing the
     * attempt-threshold logic entirely.
     */
    WHITELIST
}
