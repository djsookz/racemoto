package com.revix.app.billing

import android.app.Activity
import android.content.Context
import android.content.Intent

object ProGate {
    fun openPaywall(context: Context, feature: ProAccess.Feature = ProAccess.Feature.GENERAL) {
        val featureKey = when (feature) {
            ProAccess.Feature.DRAG -> ProPaywallActivity.FEATURE_DRAG
            ProAccess.Feature.TRACK -> ProPaywallActivity.FEATURE_TRACK
            ProAccess.Feature.DEVICES -> ProPaywallActivity.FEATURE_DEVICES
            ProAccess.Feature.PROFILE -> ProPaywallActivity.FEATURE_PROFILE
            ProAccess.Feature.NAVIGATION -> ProPaywallActivity.FEATURE_NAVIGATION
            ProAccess.Feature.SETTINGS -> ProPaywallActivity.FEATURE_SETTINGS
            ProAccess.Feature.GENERAL -> ProPaywallActivity.FEATURE_GENERAL
        }
        context.startActivity(ProPaywallActivity.intent(context, featureKey))
    }

    fun openFreeProfilePicker(context: Context) {
        val intent = FreeProfilePickerActivity.intent(context).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }

    /**
     * @return true if the user may continue with a write action on [profileId].
     */
    fun ensureWritable(context: Context, profileId: Long, feature: ProAccess.Feature = ProAccess.Feature.GENERAL): Boolean {
        ProAccess.reconcile(context)
        if (ProAccess.needsFreeProfileSelection(context)) {
            openFreeProfilePicker(context)
            return false
        }
        if (!ProAccess.canWriteProfile(context, profileId)) {
            openPaywall(context, feature)
            return false
        }
        return true
    }

    fun ensureDragOrTrack(context: Context, feature: ProAccess.Feature): Boolean {
        ProAccess.reconcile(context)
        if (ProAccess.needsFreeProfileSelection(context)) {
            openFreeProfilePicker(context)
            return false
        }
        if (!ProAccess.canUseDragOrTrack(context)) {
            openPaywall(context, feature)
            return false
        }
        return true
    }

    /**
     * Turn-by-turn uses Mapbox Navigation trip session (counts Navigation MAU).
     * Free users keep Follow line / free ride; TBT requires Pro (or debug trial).
     */
    fun ensureTurnByTurnNavigation(context: Context): Boolean {
        ProAccess.reconcile(context)
        if (ProAccess.needsFreeProfileSelection(context)) {
            openFreeProfilePicker(context)
            return false
        }
        if (!ProAccess.hasFullAccess(context)) {
            openPaywall(context, ProAccess.Feature.NAVIGATION)
            return false
        }
        return true
    }

    /** Pro / trial gate for external GPS/IMU ("RaceBox") and similar full-access features. */
    fun ensureFullAccess(context: Context, feature: ProAccess.Feature): Boolean {
        return ensureDragOrTrack(context, feature)
    }

    fun ensureCanCreateProfile(context: Context): Boolean {
        ProAccess.reconcile(context)
        if (ProAccess.needsFreeProfileSelection(context)) {
            openFreeProfilePicker(context)
            return false
        }
        if (!ProAccess.canCreateProfile(context)) {
            openPaywall(context, ProAccess.Feature.PROFILE)
            return false
        }
        return true
    }
}
