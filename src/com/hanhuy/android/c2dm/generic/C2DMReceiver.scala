package com.hanhuy.android.c2dm.generic

import android.util.Log
import android.content.{Intent, Context}
import android.preference.PreferenceManager
import com.google.android.c2dm.C2DMBaseReceiver

class C2DMReceiver extends C2DMBaseReceiver(C.SENDER_ID) {

    override def onError(context: Context, errorId: String) {
        Log.e(C.LOG, "Received error: " + errorId)
    }
    override def onMessage(context: Context, intent: Intent) = Unit
    override def onRegistered(context: Context, id: String) {
        Log.i(C.LOG, "Received registration key: " + id)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putString(C.REGISTRATION_KEY, id).commit()
    }
    override def onUnregistered(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().remove(C.REGISTRATION_KEY).commit()
    }
}
