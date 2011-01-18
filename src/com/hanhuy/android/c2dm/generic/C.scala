package com.hanhuy.android.c2dm.generic

import android.os.Build;

object C {
    val SENDER_ID = "hanhuy.c2dm@gmail.com"
    val REGISTRATION_KEY = "registrationId"
    val ACCOUNTS_KEY = "accountNames"
    val TAG = "HhC2DM"

    val RECONTROLR_SERVER = {
        if (Build.MANUFACTURER == Build.UNKNOWN && Build.MODEL == "google_sdk")
            "http://galactica0.hanhuy.com:8002"
        else
            "http://recontrolr.appspot.com"
    }
    val REGISTER_URL      = RECONTROLR_SERVER + "/device/register"
    val UNREGISTER_URL    = RECONTROLR_SERVER + "/device/unregister"

    val ACTION_UPDATE_UI  = "com.hanhuy.android.c2dm.generic.action.UPDATE_UI"
    val ACTION_GEOLOCATE  = "com.hanhuy.android.c2dm.generic.action.GEOLOCATE"
    val ACTION_DOWNLOAD   = "com.hanhuy.android.c2dm.generic.action.DOWNLOAD"
    val ACTION_JAVASCRIPT = "com.hanhuy.android.c2dm.generic.action.JAVASCRIPT"

    val EXTRA_ERROR = "error"

    val EXTRA_COMMAND = "cmd"

    val COMMAND_GEOLOCATE  = "ge"
    val COMMAND_DOWNLOAD   = "dl"
    val COMMAND_JAVASCRIPT = "js"
    val COMMAND_UNREGISTER = "ur"

    val PARAM_AUTOEXT = "ast"
    val PARAM_DELETE  = "del"
    val PARAM_ID      = "id"
    val PARAM_URL     = "url"
    // where to download URL to or file to execute
    val PARAM_TARGET  = "tgt"
    // status update responses to specified URL
    val PARAM_REPLYTO = "rto"
    // for password-protected URL (HTTP-auth only)
    val PARAM_USER    = "usr"
    val PARAM_PASS    = "pwd"
}
