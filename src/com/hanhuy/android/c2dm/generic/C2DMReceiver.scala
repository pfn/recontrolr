package com.hanhuy.android.c2dm.generic

import android.accounts.AccountManager

import android.util.Log
import android.content.{Intent, Context}
import android.preference.PreferenceManager
import com.google.android.c2dm.{C2DMBaseReceiver, C2DMessaging}

import org.json.JSONObject;

class C2DMReceiver extends C2DMBaseReceiver(C.SENDER_ID) with AccountNames {

    override def onHandleIntent(i: Intent) {
        try {
            RecontrolrRegistrar.init(this)
        } finally {
            super.onHandleIntent(i)
        }
    }
    override def onError(c: Context, errorId: String) {
        Log.e(C.TAG, "Received error: " + errorId)
        val intent = new Intent(C.ACTION_UPDATE_UI)
        intent.putExtra(C.EXTRA_ERROR, errorId)
        sendBroadcast(intent)
    }
    
    override def onMessage(c: Context, intent: Intent) {
        val command = intent.getStringExtra(C.EXTRA_COMMAND)
        val replyTo = intent.getStringExtra(C.PARAM_REPLYTO)
        val id      = intent.getStringExtra(C.PARAM_ID)
        if (replyTo == null) {
            Log.w(C.TAG, command + ": did not specify a reply-to")
            return
        }
        RecontrolrRegistrar.ack(replyTo, id)
        val clazz = command match {
            case C.COMMAND_GEOLOCATE  => classOf[GeolocateService]
            case C.COMMAND_DOWNLOAD   => classOf[DownloadService]
            case C.COMMAND_JAVASCRIPT => classOf[JavascriptService]
            case C.COMMAND_UNREGISTER => {
                unregisterService(id, replyTo)
                null
            }
            case _ => {
                Log.e(C.TAG, "Received an unknown command: " + command)
                null
            }
        }
        if (clazz != null)
            delegateService(command, clazz, intent, replyTo)
    }
    
    private def unregisterService(id: String, replyTo: String) {
        C2DMessaging.unregister(this)
        val o = new JSONObject();
        o.put("success", true);
        o.put("time", 1L)
        RecontrolrRegistrar.respond(replyTo, id, o.toString())
    }
    
    private def delegateService(cmd: String, service: Class[_],
            i: Intent, replyTo: String) {
        if (replyTo == null) return
        Log.i(C.TAG, "Processing command: " + cmd)
        val intent = new Intent(this, service)
        intent.putExtras(i)
        startService(intent)
    }

    override def onRegistered(c: Context, id: String) {
        Log.i(C.TAG, "Received registration key: " + id)
        val prefs = PreferenceManager.getDefaultSharedPreferences(c)
        prefs.edit().putString(C.REGISTRATION_KEY, id).commit()
        prefs.edit().putString(
                C.ACCOUNTS_KEY, accountNames.mkString(", ")).commit()
        sendBroadcast(new Intent(C.ACTION_UPDATE_UI ))
        RecontrolrRegistrar.register(this, id, accountNames)
    }
    override def onUnregistered(c: Context) {
        Log.i(C.TAG, "Unregistered from C2DM")
        val prefs = PreferenceManager.getDefaultSharedPreferences(c)
        val id = prefs.getString(C.REGISTRATION_KEY, null)
        prefs.edit().remove(C.REGISTRATION_KEY).commit()
        prefs.edit().remove(C.ACCOUNTS_KEY).commit()
        sendBroadcast(new Intent(C.ACTION_UPDATE_UI))
        RecontrolrRegistrar.unregister(id)
    }
}
