package com.callbloqued.sift.presentation.navigation

/**
 * Top-level navigation destinations in the Sift app.
 *
 * Each entry maps to a bottom-navigation tab and a [androidx.navigation.NavGraph] route.
 * The three destinations are peer-level screens with no hierarchical relationship, which
 * makes a bottom navigation bar the most appropriate navigation pattern.
 *
 * @property route Unique string route used by [androidx.navigation.NavHost] to identify
 *   this destination.
 */
enum class SiftDestination(val route: String) {

    /** Displays the chronological log of calls blocked by the screening service. */
    HISTORY("history"),

    /** Controls the screening toggle and the required-attempt threshold. */
    SETTINGS("settings"),

    /** Shows and edits the user-managed blacklist and whitelist. */
    MANUAL_LISTS("manual_lists")
}
