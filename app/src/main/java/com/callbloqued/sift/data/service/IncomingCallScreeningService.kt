package com.callbloqued.sift.data.service

import android.telecom.Call
import android.telecom.CallScreeningService
import com.callbloqued.sift.domain.model.CallDecision
import com.callbloqued.sift.domain.usecase.EvaluateIncomingCallUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Android entry point that receives incoming call events from the telecom stack and
 * delegates the allow/block decision to [EvaluateIncomingCallUseCase].
 *
 * This class lives in the `data` layer because it is an Android framework component (an
 * adapter between the OS and the domain logic) rather than business logic itself. It has no
 * direct knowledge of the decision rules; it only translates the [CallDecision] produced by
 * the use case into a [CallResponse] understood by the telecom stack.
 *
 * The service must be declared in `AndroidManifest.xml` with
 * `android:permission="android.permission.BIND_SCREENING_SERVICE"` and an intent filter for
 * `android.telecom.CallScreeningService`. The user must also grant the app the
 * `ROLE_CALL_SCREENING` role via [android.app.role.RoleManager] (handled in F5 onboarding).
 *
 * **Coroutine lifecycle:** a [CoroutineScope] backed by [SupervisorJob] is created when the
 * service starts and cancelled in [onDestroy]. [SupervisorJob] ensures that a failure in one
 * screening coroutine does not cancel ongoing evaluations for other simultaneous calls (edge
 * case, but possible on multi-SIM devices).
 */
@AndroidEntryPoint
class IncomingCallScreeningService : CallScreeningService() {

    /** Use case that contains the call-screening decision logic. Injected by Hilt. */
    @Inject
    lateinit var evaluateIncomingCall: EvaluateIncomingCallUseCase

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Called by the Android telecom framework for each incoming call before it rings.
     *
     * Extracts the raw caller number from [callDetails], delegates to [evaluateIncomingCall],
     * and calls [respondToCall] with the translated [CallResponse]. The evaluation runs on
     * [Dispatchers.IO] to avoid blocking the main thread during database and ContentResolver
     * access.
     *
     * If the caller's number is unavailable (private/unknown, `handle` is null), an empty
     * string is passed to the use case, which normalises it to `null` and fails open
     * (allows the call) to avoid blocking a number we cannot identify.
     *
     * @param callDetails Details of the incoming call provided by the telecom framework.
     */
    override fun onScreenCall(callDetails: Call.Details) {
        serviceScope.launch {
            val rawNumber = callDetails.handle?.schemeSpecificPart.orEmpty()
            val decision = evaluateIncomingCall(rawNumber)
            respondToCall(callDetails, buildCallResponse(decision))
        }
    }

    /**
     * Cancels the [serviceScope] to release coroutine resources when the service is destroyed.
     */
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    /**
     * Translates a [CallDecision] into a [CallResponse] for the Android telecom stack.
     *
     * [CallDecision.Allow] produces an empty response (all flags default to false = let ring).
     * [CallDecision.DisallowSilently] sets `disallowCall = true` and `rejectCall = false` so
     * that the call is terminated on the user's side without sending a rejection signal to
     * the caller (they hear nothing). The call is left in the native call log
     * (`skipCallLog` is not set) for transparency, in addition to the in-app history.
     *
     * @param decision The [CallDecision] returned by [EvaluateIncomingCallUseCase].
     * @return A [CallResponse] ready to be passed to [respondToCall].
     */
    private fun buildCallResponse(decision: CallDecision): CallResponse {
        val builder = CallResponse.Builder()
        return when (decision) {
            is CallDecision.Allow -> builder.build()
            is CallDecision.DisallowSilently -> builder
                .setDisallowCall(true)
                .setRejectCall(false)
                .build()
        }
    }
}
