package com.callbloqued.sift.domain.model

/**
 * Represents the outcome produced by [com.callbloqued.sift.domain.usecase.EvaluateIncomingCallUseCase]
 * for a single incoming call.
 *
 * Each subtype maps directly to a [android.telecom.CallScreeningService.CallResponse] action,
 * but this sealed class itself has no Android dependencies so it can be reasoned about and
 * tested entirely in the domain layer.
 */
sealed class CallDecision {

    /**
     * The call is allowed to ring on the user's device without any modification.
     *
     * Issued when the caller is a known contact, when the screening filter is disabled,
     * or when the caller has already surpassed the configured attempt threshold.
     */
    data object Allow : CallDecision()

    /**
     * The call is rejected silently: the caller receives no rejection signal and the device
     * does not ring. The blocked attempt is always recorded in the local history so that the
     * user can review it and add the number to a whitelist if needed.
     *
     * Issued when the caller is unknown and has not yet reached the configured attempt threshold.
     */
    data object DisallowSilently : CallDecision()
}
