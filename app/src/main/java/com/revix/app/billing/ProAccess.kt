package com.revix.app.billing

import android.content.Context
import androidx.preference.PreferenceManager
import com.revix.app.BuildConfig
import com.revix.app.data.ProfileStorage

/**
 * Freemium rules:
 * - Free (no trial/Pro): 1 profile writable (Map/Garage/Reports); Drag/Track/external GPS locked.
 * - Pro / (debug) trial: up to 5 profiles, full access.
 * - After Pro/trial ends with 2+ profiles: blocking one-time pick of free writable profile;
 *   others read-only until Pro again. Choice is cleared whenever Pro becomes active again.
 *
 * NOTE: Pro unlock comes from Google Play subscriptions (revix_pro_monthly / revix_pro_yearly).
 * Debug builds can also use a local preview flag.
 */
object ProAccess {
    private const val PREFS_NAME = "revix_free_profile"
    private const val PREF_PRO_PREVIEW = "revix_pro_preview_unlocked"
    private const val PREF_TRIAL_STARTED_AT = "revix_trial_started_at"
    private const val PREF_FREE_PROFILE_ID = "revix_free_profile_id"
    private const val PREF_FREE_PROFILE_CHOSEN = "revix_free_profile_chosen"
    /** Legacy keys lived in default SharedPreferences before dedicated prefs file. */
    private const val LEGACY_PREF_FREE_PROFILE_ID = "revix_free_profile_id"
    private const val LEGACY_PREF_FREE_PROFILE_CHOSEN = "revix_free_profile_chosen"

    const val MAX_PROFILES = 5
    const val MAX_FREE_PROFILES = 1
    const val TRIAL_DURATION_MS = 7L * 24L * 60L * 60L * 1000L

    enum class Feature { DRAG, TRACK, DEVICES, PROFILE, NAVIGATION, SETTINGS, GENERAL }

    private fun freePrefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isPro(context: Context): Boolean {
        if (BuildConfig.DEBUG &&
            PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_PRO_PREVIEW, false)
        ) {
            return true
        }
        return PlayBillingManager.hasCachedProEntitlement(context)
    }

    fun setProPreview(context: Context, unlocked: Boolean) {
        if (!BuildConfig.DEBUG) return
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(PREF_PRO_PREVIEW, unlocked)
            .apply()
        // Same as real Pro: clear free-profile lock so the next downgrade asks again.
        if (unlocked) clearFreeProfileChoice(context)
    }

    fun hasTrialEverStarted(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getLong(PREF_TRIAL_STARTED_AT, 0L) > 0L
    }

    fun isTrialActive(context: Context): Boolean {
        val startedAt = PreferenceManager.getDefaultSharedPreferences(context)
            .getLong(PREF_TRIAL_STARTED_AT, 0L)
        if (startedAt <= 0L) return false
        return System.currentTimeMillis() - startedAt < TRIAL_DURATION_MS
    }

    fun trialMillisRemaining(context: Context): Long {
        val startedAt = PreferenceManager.getDefaultSharedPreferences(context)
            .getLong(PREF_TRIAL_STARTED_AT, 0L)
        if (startedAt <= 0L) return 0L
        val end = startedAt + TRIAL_DURATION_MS
        return (end - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun trialDaysRemaining(context: Context): Int {
        val ms = trialMillisRemaining(context)
        if (ms <= 0L) return 0
        return ((ms + 23L * 60L * 60L * 1000L) / (24L * 60L * 60L * 1000L)).toInt().coerceAtLeast(1)
    }

    fun canStartTrial(context: Context): Boolean {
        // Local device trial is debug-only. Release uses Google Play free-trial offers
        // (tied to the Google account, so reinstall cannot reset it).
        if (!BuildConfig.DEBUG) return false
        return !isPro(context) && !hasTrialEverStarted(context)
    }

    fun startTrial(context: Context): Boolean {
        if (!canStartTrial(context)) return false
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putLong(PREF_TRIAL_STARTED_AT, System.currentTimeMillis())
            .apply()
        clearFreeProfileChoice(context)
        return true
    }

    fun hasChosenFreeProfile(context: Context): Boolean {
        migrateLegacyFreeProfilePrefs(context)
        return freePrefs(context).getBoolean(PREF_FREE_PROFILE_CHOSEN, false)
    }

    fun getFreeProfileId(context: Context): Long {
        migrateLegacyFreeProfilePrefs(context)
        return freePrefs(context).getLong(PREF_FREE_PROFILE_ID, -1L)
    }

    fun setFreeProfileChoice(context: Context, profileId: Long) {
        freePrefs(context)
            .edit()
            .putLong(PREF_FREE_PROFILE_ID, profileId)
            .putBoolean(PREF_FREE_PROFILE_CHOSEN, true)
            .apply()
        clearLegacyFreeProfilePrefs(context)
        ProfileStorage.saveSelectedProfile(context, profileId)
    }

    fun clearFreeProfileChoice(context: Context) {
        freePrefs(context)
            .edit()
            .remove(PREF_FREE_PROFILE_ID)
            .putBoolean(PREF_FREE_PROFILE_CHOSEN, false)
            .apply()
        clearLegacyFreeProfilePrefs(context)
    }

    /**
     * Call when Play (or debug Pro) entitlement is known.
     * Pro active → clear any free-profile lock so the next downgrade always re-asks.
     */
    fun onEntitlementChanged(context: Context, entitled: Boolean) {
        if (entitled) {
            clearFreeProfileChoice(context)
        } else {
            reconcile(context)
        }
    }

    /** Call on app resume / before gated actions. */
    fun reconcile(context: Context) {
        clearRestoredLocalTrialOnRelease(context)
        migrateLegacyFreeProfilePrefs(context)

        if (hasFullAccess(context)) {
            // Never keep a free-profile lock while Pro/trial is active.
            if (hasChosenFreeProfile(context)) clearFreeProfileChoice(context)
            return
        }

        val profiles = ProfileStorage.loadProfiles(context)
        // One (or zero) vehicle is always the free profile. A leftover lock from
        // Pro / backup / an old auto-pick must not block Map, Follow line, or Garage.
        if (profiles.size <= 1) {
            if (hasChosenFreeProfile(context)) clearFreeProfileChoice(context)
            return
        }

        if (!hasChosenFreeProfile(context)) return

        val chosenId = getFreeProfileId(context)
        if (chosenId <= 0L || profiles.none { it.id == chosenId }) {
            clearFreeProfileChoice(context)
        }
    }

    fun needsFreeProfileSelection(context: Context): Boolean {
        reconcile(context)
        if (hasFullAccess(context)) return false
        if (ProfileStorage.loadProfiles(context).size <= 1) return false
        return !hasChosenFreeProfile(context)
    }

    fun hasFullAccess(context: Context): Boolean {
        if (isPro(context)) return true
        // Local device trial must never unlock release builds (Auto Backup can restore it).
        return BuildConfig.DEBUG && isTrialActive(context)
    }

    /**
     * Local trial prefs can be restored by Auto Backup after a Play-trial user loses Pro.
     * That must not keep blocking free-profile selection or fake "trial active" UI in release.
     */
    private fun clearRestoredLocalTrialOnRelease(context: Context) {
        if (BuildConfig.DEBUG) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getLong(PREF_TRIAL_STARTED_AT, 0L) > 0L) {
            prefs.edit().remove(PREF_TRIAL_STARTED_AT).apply()
        }
    }

    private fun migrateLegacyFreeProfilePrefs(context: Context) {
        val prefs = freePrefs(context)
        if (prefs.contains(PREF_FREE_PROFILE_CHOSEN)) return
        val legacy = PreferenceManager.getDefaultSharedPreferences(context)
        if (!legacy.contains(LEGACY_PREF_FREE_PROFILE_CHOSEN) &&
            !legacy.contains(LEGACY_PREF_FREE_PROFILE_ID)
        ) {
            return
        }
        // Discard legacy auto-lock from the old "1 profile → silent main" path.
        // Free users with 2+ profiles will be prompted again (correct).
        clearLegacyFreeProfilePrefs(context)
        prefs.edit()
            .putBoolean(PREF_FREE_PROFILE_CHOSEN, false)
            .remove(PREF_FREE_PROFILE_ID)
            .apply()
    }

    private fun clearLegacyFreeProfilePrefs(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .remove(LEGACY_PREF_FREE_PROFILE_ID)
            .remove(LEGACY_PREF_FREE_PROFILE_CHOSEN)
            .apply()
    }

    fun canUseDragOrTrack(context: Context): Boolean = hasFullAccess(context)

    fun canCreateProfile(context: Context): Boolean {
        val count = ProfileStorage.loadProfiles(context).size
        return if (hasFullAccess(context)) {
            count < MAX_PROFILES
        } else {
            count < MAX_FREE_PROFILES
        }
    }

    fun maxProfilesAllowed(context: Context): Int {
        return if (hasFullAccess(context)) MAX_PROFILES else MAX_FREE_PROFILES
    }

    fun canWriteProfile(context: Context, profileId: Long): Boolean {
        if (hasFullAccess(context)) return true

        val profiles = ProfileStorage.loadProfiles(context)
        if (profiles.size <= 1) {
            val only = profiles.singleOrNull() ?: return false
            return profileId <= 0L || profileId == only.id
        }

        if (needsFreeProfileSelection(context)) return false
        if (profileId <= 0L) return false
        return hasChosenFreeProfile(context) && profileId == getFreeProfileId(context)
    }

    // --- Preview / debug helpers ---

    fun resetTrialForPreview(context: Context) {
        if (!BuildConfig.DEBUG) return
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .remove(PREF_TRIAL_STARTED_AT)
            .apply()
        clearFreeProfileChoice(context)
    }

    fun expireTrialForPreview(context: Context) {
        if (!BuildConfig.DEBUG) return
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putLong(PREF_TRIAL_STARTED_AT, System.currentTimeMillis() - TRIAL_DURATION_MS - 1000L)
            .apply()
        clearFreeProfileChoice(context)
    }
}
