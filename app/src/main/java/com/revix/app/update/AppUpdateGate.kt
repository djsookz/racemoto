package com.revix.app.update

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.FirebaseFirestore
import com.revix.app.BuildConfig
import com.revix.app.DialogHelper
import com.revix.app.R

/**
 * Blocks the app when Firestore reports a higher minimum versionCode.
 *
 * Firestore: collection `app_config`, document `android`
 * - minVersionCode (number)
 * - forceUpdate (boolean)
 * - storeUrl (string, optional)
 *
 * Fail-open on network/errors so offline use is not locked out.
 */
object AppUpdateGate {

    private const val TAG = "AppUpdateGate"
    private const val COLLECTION = "app_config"
    private const val DOCUMENT = "android"
    private val defaultStoreUrl = "market://details?id=${BuildConfig.APPLICATION_ID}"
    private val webStoreUrl =
        "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"

    @Volatile
    private var dialogShowing = false

    private var activeDialog: AlertDialog? = null

    /**
     * @param onAllowed invoked when the installed version is allowed (or check fails open).
     *                  Not called when a force-update dialog is shown.
     */
    fun check(activity: Activity, onAllowed: (() -> Unit)? = null) {
        if (activity.isFinishing) return

        FirebaseFirestore.getInstance()
            .collection(COLLECTION)
            .document(DOCUMENT)
            .get()
            .addOnSuccessListener { snapshot ->
                if (activity.isFinishing || activity.isDestroyed) return@addOnSuccessListener

                if (!snapshot.exists()) {
                    onAllowed?.invoke()
                    return@addOnSuccessListener
                }

                val minVersionCode = snapshot.getLong("minVersionCode")?.toInt() ?: 0
                val forceUpdate = snapshot.getBoolean("forceUpdate") ?: false
                val storeUrl = snapshot.getString("storeUrl")
                    ?.takeIf { it.isNotBlank() }

                val needsForceUpdate =
                    forceUpdate && BuildConfig.VERSION_CODE < minVersionCode

                if (needsForceUpdate) {
                    Log.i(
                        TAG,
                        "Force update required: installed=${BuildConfig.VERSION_CODE}, min=$minVersionCode"
                    )
                    showForceUpdateDialog(activity, storeUrl)
                } else {
                    onAllowed?.invoke()
                }
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Version check failed (fail-open)", error)
                if (!activity.isFinishing && !activity.isDestroyed) {
                    onAllowed?.invoke()
                }
            }
    }

    private fun showForceUpdateDialog(activity: Activity, storeUrl: String?) {
        if (dialogShowing && activeDialog?.isShowing == true) return
        if (activity.isFinishing || activity.isDestroyed) return

        activeDialog?.dismiss()
        dialogShowing = true

        val dialog = DialogHelper.builder(activity)
            .setTitle(R.string.force_update_title)
            .setMessage(R.string.force_update_message)
            .setCancelable(false)
            .setPositiveButton(R.string.force_update_button) { _, _ ->
                openPlayStore(activity, storeUrl)
                // Keep gate armed so onResume can re-show if still outdated.
                dialogShowing = false
                activeDialog = null
            }
            .create()

        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener {
            if (activeDialog === dialog) {
                dialogShowing = false
                activeDialog = null
            }
        }

        activeDialog = dialog
        DialogHelper.show(dialog)
    }

    private fun openPlayStore(activity: Activity, storeUrl: String?) {
        val candidates = buildList {
            storeUrl?.let { add(it) }
            add(defaultStoreUrl)
            add(webStoreUrl)
        }

        for (url in candidates) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(activity.packageManager) != null) {
                runCatching { activity.startActivity(intent) }
                    .onSuccess { return }
                    .onFailure { Log.w(TAG, "Failed to open store url=$url", it) }
            }
        }
        Log.e(TAG, "No activity found to open Play Store")
    }
}
