package com.sdk.ads.consent

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.sdk.ads.utils.logEvent

class GdprConsent(val context: Context, private val language: String) {
    @Suppress("PrivatePropertyName")
    private val TAG = "GdprConsent"
    private val consentInformation = UserMessagingPlatform.getConsentInformation(context)
    private var consentForm: ConsentForm? = null
    /**IN PRODUCTION CALL AT ONCREATE FOR CONSENT FORM CHECK*/
    fun updateConsentInfo(
        activity: Activity,
        underAge: Boolean,
        consentTracker: ConsentTracker,
        isShowForceAgain: Boolean = false,
        consentPermit: (Boolean) -> Unit,
        initAds: () -> Unit
    ) {
        val params = ConsentRequestParameters
            .Builder()
            // .setAdMobAppId(context.getString(R.string.AdMob_App_ID))
            .setTagForUnderAgeOfConsent(underAge)
            .build()
        requestConsentInfoUpdate(
            activity = activity,
            params = params,
            consentPermit = consentPermit,
            isShowForceAgain = isShowForceAgain,
            consentTracker = consentTracker,
            initAds = { initAds() }
        )
    }

    /**ONLY TO DEBUG EU & NONE EU GEOGRAPHICS
     * EU: ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
     * NOT EU: ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_NOT_EEA
     * DISABLED: ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_DISABLED
     * requestConsentInfoUpdate() logs the hashed id when run*/
    fun updateConsentInfoWithDebugGeoGraphics(
        activity: Activity,
        geoGraph: Int = ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA,
        consentTracker: ConsentTracker,
        isShowForceAgain: Boolean = false,
        consentPermit: (Boolean) -> Unit,
        initAds: () -> Unit,
        hashDeviceIdTest: List<String>?,
    ) {
        val debugSetting = ConsentDebugSettings.Builder(context)
            .setDebugGeography(geoGraph)
            .addTestDeviceHashedId(AdRequest.DEVICE_ID_EMULATOR)
        hashDeviceIdTest?.forEach {
            debugSetting.addTestDeviceHashedId(it)
        }
        val debugSettings = debugSetting.build()
        val params = ConsentRequestParameters
            .Builder()
            .setConsentDebugSettings(debugSettings)
            // .setAdMobAppId(context.getString(R.string.AdMob_App_ID))
            .build()
        Log.e("requestConsent:::", "requestConsentInfoUpdate")
        requestConsentInfoUpdate(
            activity = activity,
            params = params,
            consentTracker = consentTracker,
            isShowForceAgain = isShowForceAgain,
            consentPermit = consentPermit,
            initAds = { initAds() }
        )
    }

    private fun requestConsentInfoUpdate(
        activity: Activity,
        params: ConsentRequestParameters,
        consentTracker: ConsentTracker,
        isShowForceAgain: Boolean = false,
        consentPermit: (Boolean) -> Unit,
        initAds: () -> Unit
    ) {
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            { // The consent information state was updated, ready to check if a form is available.
                if (consentInformation.isConsentFormAvailable) {
                    loadForm(activity, consentTracker, isShowForceAgain, consentPermit, initAds = { initAds() })
                } else {
                    consentPermit(isConsentObtained(consentTracker))
                }
            },
            { formError ->
                initAds()
                Log.e(TAG, "requestConsentInfoUpdate: ${formError.message}")
            }
        )
    }

    private fun loadForm(
        activity: Activity,
        consentTracker: ConsentTracker,
        isShowForceAgain: Boolean = false,
        consentPermit: (Boolean) -> Unit,
        initAds: () -> Unit
    ) {
        Log.e(TAG, "requestConsentInfoUpdate:loadConsentForm")
        // Loads a consent form. Must be called on the main thread.
        UserMessagingPlatform.loadConsentForm(
            context,
            { consentFormShow ->
                if (consentForm != null) return@loadConsentForm
                // Take form if needed later
                consentForm = consentFormShow
                Log.e(TAG, "consentForm is required to show" + consentInformation.consentStatus.toString())
                when (consentInformation.consentStatus) {
                    ConsentInformation.ConsentStatus.REQUIRED -> {
                        Log.e(TAG, "consentForm is required to show:::${consentForm}")
                        if (isShowForceAgain) {
                            logEvent(evenName = "GDPR3_showFormGDPR_$language")
                        } else {
                            logEvent(evenName = "consent_showFormGDPR_$language")
                        }
                        consentForm?.show(
                            activity,
                        ) { formError ->
                            // Log error
                            if (formError != null) {
                                Log.e(TAG, "consentForm show ${formError.message}")
                                if (isShowForceAgain) {
                                    logEvent(evenName = "GDPR3_formError_${formError.errorCode}_${formError.message}_$language")
                                } else {
                                    logEvent(evenName = "GDPR_formError_${formError.errorCode}_${formError.message}_$language")
                                }
                            }
                            // App can start requesting ads.
                            if (consentInformation.consentStatus == ConsentInformation.ConsentStatus.OBTAINED) {
                                Log.e(TAG, "consentForm is Obtained")
                                consentPermit(isConsentObtained(consentTracker, isTracking = true))
                                initAds()
                            }
                            Log.e(TAG, "consentForm is required to show${consentForm}")
                            // Handle dismissal by reloading form.
                            loadForm(activity, consentTracker, isShowForceAgain, consentPermit, initAds)
                        }
                    }

                    else -> {
                        consentPermit(isConsentObtained(consentTracker))
                    }
                }
            },
            { formError ->
                Log.e(TAG, "loadForm Failure: ${formError.message}")
                if (isShowForceAgain) {
                    logEvent(evenName = "GDPR3_formError_${formError.errorCode}_${formError.message}_$language")
                } else {
                    logEvent(evenName = "GDPR_formError_${formError.errorCode}_${formError.message}_$language")
                }
            },
        )
    }

    fun reUseExistingConsentForm(
        activity: Activity,
        consentTracker: ConsentTracker,
        consentPermit: (Boolean) -> Unit,
        initAds: () -> Unit
    ) {
        resetConsent()
        if (consentInformation.isConsentFormAvailable) {
            Log.e(TAG, "reUseExistingConsentForm" + consentForm.toString())
            logEvent("GDPR3_showFormGDPR_$language")
            consentForm?.show(
                activity,
            ) { formError ->
                // Log error
                if (formError != null) {
                    Log.e(TAG, "consentForm formError ${formError.message}")
                }
                // App can start requesting ads.
                if (consentInformation.consentStatus == ConsentInformation.ConsentStatus.OBTAINED) {
                    Log.e(TAG, "consentForm is Obtained")
                    consentPermit(isConsentObtained(consentTracker))
                    initAds()
                }
                // Handle dismissal by reloading form.
                loadForm(activity, consentTracker, true, consentPermit, initAds)
            }
        } else {
            Log.e(TAG, "Consent form not available, check internet connection.")
            consentPermit(isConsentObtained(consentTracker))
        }
    }

    /**RETURNS TRUE IF EU/UK IS TRULY OBTAINED OR NOT REQUIRED ELSE FALSE*/
    private fun isConsentObtained(consentTracker: ConsentTracker, isTracking: Boolean = false): Boolean {
        val obtained = consentTracker.isUserConsentValid(isTracking) && consentInformation.consentStatus == ConsentInformation.ConsentStatus.OBTAINED
        val notRequired = consentInformation.consentStatus == ConsentInformation.ConsentStatus.NOT_REQUIRED
        val isObtained = obtained || notRequired
        Log.e(TAG, "isConsentObtained or not required: $isObtained")
        return isObtained
    }

    fun canRequestAds(): Boolean {
        return consentInformation.canRequestAds()
    }

    /**RESET ONLY IF TRULY REQUIRED. E.G FOR TESTING OR USER WANTS TO RESET CONSENT SETTINGS*/
    fun resetConsent() {
        consentInformation.reset()
    }
}