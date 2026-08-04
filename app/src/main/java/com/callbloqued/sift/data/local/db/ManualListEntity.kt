package com.callbloqued.sift.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity that persists a single entry in the user-managed blacklist or whitelist.
 *
 * **Primary key design:** [phoneNumber] is the sole primary key, enforcing mutual exclusivity —
 * a phone number can belong to at most one list at a time. A composite key (phone + type) was
 * considered but rejected: allowing a number to appear simultaneously in both lists creates an
 * ambiguous screening decision and is not a valid product state. Using [phoneNumber] alone as
 * the PK means that inserting a number already present in one list with a different [listType]
 * replaces the existing row via [androidx.room.OnConflictStrategy.REPLACE], effectively moving
 * the number to the new list without leaving orphan rows.
 *
 * The [listType] column stores the [com.callbloqued.sift.domain.model.ManualListType] enum
 * value by name (e.g. `"BLACKLIST"`) so that reordering enum entries in future phases does
 * not corrupt existing records.
 *
 * Numbers are stored in E.164 format after normalisation by
 * [com.callbloqued.sift.domain.util.PhoneNumberNormalizer].
 *
 * Timestamps are Unix epoch milliseconds ([System.currentTimeMillis]).
 */
@Entity(tableName = "manual_list")
data class ManualListEntity(

    /**
     * Phone number in E.164 format. Acts as the primary key; one row per unique phone number
     * across both the blacklist and whitelist combined.
     */
    @PrimaryKey
    @ColumnInfo(name = "phone_number")
    val phoneNumber: String,

    /**
     * Name of the [com.callbloqued.sift.domain.model.ManualListType] enum value indicating
     * whether this entry is in the blacklist or whitelist (e.g. `"BLACKLIST"` or `"WHITELIST"`).
     */
    @ColumnInfo(name = "list_type")
    val listType: String,

    /**
     * Timestamp (epoch ms) when the user added this number to the list.
     */
    @ColumnInfo(name = "added_at")
    val addedAt: Long
)
