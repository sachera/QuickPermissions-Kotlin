package com.livinglifetechway.quickpermissions_kotlin.util


import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri.fromParts
import android.os.Bundle
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import android.util.Log
import androidx.appcompat.app.AlertDialog

/**
 * A simple [Fragment] subclass.
 */
class PermissionCheckerFragment : Fragment() {

    private var quickPermissionsRequest: QuickPermissionsRequest? = null

    interface QuickPermissionsCallback {
        fun shouldShowRequestPermissionsRationale(quickPermissionsRequest: QuickPermissionsRequest?)
        fun onPermissionsGranted(quickPermissionsRequest: QuickPermissionsRequest?)
        fun onPermissionsPermanentlyDenied(quickPermissionsRequest: QuickPermissionsRequest?)
        fun onPermissionsDenied(quickPermissionsRequest: QuickPermissionsRequest?)
    }

    companion object {
        private val TAG = Companion::class.java.simpleName
        private const val PERMISSIONS_REQUEST_CODE = 199
        fun newInstance(): PermissionCheckerFragment = PermissionCheckerFragment()
    }

    var mListener: QuickPermissionsCallback? = null

    fun setListener(listener: QuickPermissionsCallback) {
        mListener = listener
        Log.d(TAG, "onCreate: listeners set")
    }

    fun removeListener() {
        mListener = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate: permission fragment created")
    }

    fun setRequestPermissionsRequest(quickPermissionsRequest: QuickPermissionsRequest?) {
        this.quickPermissionsRequest = quickPermissionsRequest
    }

    fun removeRequestPermissionsRequest() {
        quickPermissionsRequest = null
    }

    fun clean() {
        // permission request flow is finishing
        // let the caller receive callback about it
        if (quickPermissionsRequest?.deniedPermissions?.size ?: 0 > 0)
            mListener?.onPermissionsDenied(quickPermissionsRequest)

        removeRequestPermissionsRequest()
        removeListener()
    }

    fun requestPermissionsFromUser() {
        Log.d(TAG, "requestPermissionsFromUser: requesting permissions")
        requestPermissions(quickPermissionsRequest?.permissions.orEmpty(), PERMISSIONS_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "passing callback")

        // check if permissions granted
        handlePermissionResult(permissions, grantResults)
    }

    /**
     * Checks and takes the action based on permission results retrieved from onRequestPermissionsResult
     * and from the settings activity
     *
     * @param permissions List of Permissions
     * @param grantResults A list of permission result <b>Granted</b> or <b>Denied</b>
     */
    private fun handlePermissionResult(permissions: Array<String>, grantResults: IntArray) {
        if (PermissionsUtil.hasSelfPermission(context, permissions)) {
            // we are good to go!
            mListener?.onPermissionsGranted(quickPermissionsRequest)

            // flow complete
            clean()
        } else {
            // we are still missing permissions
            val deniedPermissions = PermissionsUtil.getDeniedPermissions(permissions, grantResults)
            quickPermissionsRequest?.deniedPermissions = deniedPermissions

            // check if rationale dialog should be shown or not
            var shouldShowRationale = true
            var isPermanentlyDenied = false
            for (i in 0 until deniedPermissions.size) {
                val deniedPermission = deniedPermissions[i]
                val rationale = shouldShowRequestPermissionRationale(deniedPermission)
                if (!rationale) {
                    shouldShowRationale = false
                    isPermanentlyDenied = true
                    break
                }
            }

            if (quickPermissionsRequest?.handlePermanentlyDenied == true && isPermanentlyDenied) {

                quickPermissionsRequest?.permanentDeniedMethod?.let {
                    // get list of permanently denied methods
                    quickPermissionsRequest?.permanentlyDeniedPermissions =
                            PermissionsUtil.getPermanentlyDeniedPermissions(this, permissions, grantResults)
                    mListener?.onPermissionsPermanentlyDenied(quickPermissionsRequest)
                    return
                }

                context?.let {
                    AlertDialog.Builder(it)
                            .setMessage(quickPermissionsRequest?.permanentlyDeniedMessage.orEmpty())
                            .setPositiveButton("SETTINGS") { _,_ -> openAppSettings() }
                            .setPositiveButton("CANCEL") { _,_ -> clean() }
                            .setCancelable(false)
                            .create()
                            .show()
                }
                return
            }

            // if should show rationale dialog
            if (quickPermissionsRequest?.handleRationale == true && shouldShowRationale) {

                quickPermissionsRequest?.rationaleMethod?.let {
                    mListener?.shouldShowRequestPermissionsRationale(quickPermissionsRequest)
                    return
                }

                context?.let {
                    AlertDialog.Builder(it)
                            .setMessage(quickPermissionsRequest?.rationaleMessage.orEmpty())
                            .setPositiveButton("TRY AGAIN") { _,_ -> requestPermissionsFromUser() }
                            .setPositiveButton("CANCEL") { _,_ -> clean() }
                            .setCancelable(false)
                            .create()
                            .show()
                }
            }
        }
    }

    fun openAppSettings() {
        val intent = Intent(ACTION_APPLICATION_DETAILS_SETTINGS,
                fromParts("package", activity?.packageName, null))
        //                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityForResult(intent, PERMISSIONS_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            val permissions = quickPermissionsRequest?.permissions ?: emptyArray()
            val grantResults = IntArray(permissions.size)
            permissions.forEachIndexed { index, s ->
                grantResults[index] = context?.let { ActivityCompat.checkSelfPermission(it, s) } ?: PackageManager.PERMISSION_DENIED
            }

            handlePermissionResult(permissions, grantResults)
        }
    }
}
