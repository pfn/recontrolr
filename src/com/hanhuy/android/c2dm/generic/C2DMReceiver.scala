package com.hanhuy.android.c2dm.generic

import android.accounts.AccountManager

import android.util.Log
import android.content.{Intent, Context}
import android.preference.PreferenceManager
import com.google.android.c2dm.{C2DMBaseReceiver, C2DMessaging}

import org.json.JSONObject;

class C2DMReceiver extends C2DMBaseReceiver(C.SENDER_ID) with AccountNames {

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
        command match {
            case C.COMMAND_GEOLOCATE  => startGeolocate(intent, replyTo)
            case C.COMMAND_DOWNLOAD   => startDownload(intent, replyTo)
            case C.COMMAND_JAVASCRIPT => startJavaScript(intent, replyTo)
            case C.COMMAND_UNREGISTER => unregisterService(id, replyTo)
            case _ => Log.e(C.TAG, "Received an unknown command: " + command)
        }
    }
    
    private def unregisterService(id: String, replyTo: String) {
        C2DMessaging.unregister(this)
        val o = new JSONObject();
        o.put("success", true);
        o.put("time", 1L)
        RecontrolrRegistrar.respond(replyTo, id, o.toString())
    }
    
    private def startJavaScript(i: Intent, replyTo: String) {
        if (replyTo == null) return
        Log.i(C.TAG, "Processing javascript command")
        val intent = new Intent(this, classOf[JavascriptService])
        intent.putExtra(C.PARAM_REPLYTO, replyTo)
        if (i.hasExtra(C.PARAM_DELETE))
            intent.putExtra(C.PARAM_DELETE, true)
        intent.putExtra(C.PARAM_TARGET, i.getStringExtra(C.PARAM_TARGET))
        intent.putExtra(C.PARAM_ID, i.getStringExtra(C.PARAM_ID))
        startService(intent)
        
    }
    private def startDownload(i: Intent, replyTo: String) {
        if (replyTo == null) return
        Log.i(C.TAG, "Processing download command")
        val intent = new Intent(this, classOf[DownloadService])
        intent.putExtra(C.PARAM_REPLYTO, replyTo)
        intent.putExtra(C.PARAM_ID, i.getStringExtra(C.PARAM_ID))
        intent.putExtra(C.PARAM_URL, i.getStringExtra(C.PARAM_URL))
        intent.putExtra(C.PARAM_TARGET, i.getStringExtra(C.PARAM_TARGET))
        if (i.hasExtra(C.PARAM_USER))
            intent.putExtra(C.PARAM_USER, i.getStringExtra(C.PARAM_USER))
        if (i.hasExtra(C.PARAM_PASS))
            intent.putExtra(C.PARAM_PASS, i.getStringExtra(C.PARAM_PASS))
        startService(intent)
    }
    private def startGeolocate(i: Intent, replyTo: String) {
        if (replyTo == null) return
        Log.i(C.TAG, "Processing geolocate command")
        val intent = new Intent(this, classOf[GeolocateService])
        intent.putExtra(C.PARAM_ID, i.getStringExtra(C.PARAM_ID))
        intent.putExtra(C.PARAM_REPLYTO, replyTo)
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