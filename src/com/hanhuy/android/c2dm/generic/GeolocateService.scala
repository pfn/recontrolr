package com.hanhuy.android.c2dm.generic

import android.app.Service
import android.content.Context
import android.location._
import android.os.{Bundle, Handler, HandlerThread, Looper, SystemClock, IBinder}
import android.util.Log
import android.content.Intent

import org.json.JSONObject

import Conversions._

class GeolocateService extends Service {

    var handler: Handler = _

    lazy val lm = getSystemService(
            Context.LOCATION_SERVICE).asInstanceOf[LocationManager]

    def gpsEnabled : Boolean =
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    def netEnabled : Boolean =
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    override def onBind(i: Intent) : IBinder = null

    override def onCreate() {
        super.onCreate()

        val t = new HandlerThread("GeolocateService")
        t.start()
        handler = new Handler(t.getLooper())
    }

    override def onDestroy() {
        handler.getLooper().quit()
    }

    override def onStartCommand(i: Intent, flags: Int, startId: Int) : Int = {
        handler.post(() => onHandleIntent(i, startId))
        Service.START_REDELIVER_INTENT
    }

    private def onHandleIntent(i: Intent, startId: Int) {
        val replyTo = i.getStringExtra(C.PARAM_REPLYTO)
        val id = i.getStringExtra(C.PARAM_ID)
        try {
            getLocation(id, replyTo, startId)
        }
        catch {
            case e: Exception => {
                Log.e(C.TAG, "Unable to query location", e)
                stopSelf(startId)
            }
        }
    }
    
    // TODO FIXME
    private def bestLocation(l1: Location, l2: Location) : Location = {
        if (l1 == null) return l2
        if (l2 == null) return l1

        return l2
    }

    private def getLocation(id: String, replyTo: String, startId: Int) {
        val start = SystemClock.elapsedRealtime()
        
        var finish: Runnable = null
        var complete: Boolean = false

        var loc: Location = null
        var l1:  Location = null
        var l2:  Location = null

        var error: String = null

        if (gpsEnabled)
            l1 = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        if (netEnabled)
            l2 = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        loc = bestLocation(l1, l2)
        Log.d(C.TAG, "Last GPS location: " + l1)
        Log.d(C.TAG, "Last NET location: " + l2)
        Log.d(C.TAG, "Best location: " + loc)

        val gpsListener = new GpsStatus.Listener {
            override def onGpsStatusChanged(status: Int) {
                if (status == GpsStatus.GPS_EVENT_FIRST_FIX) {
                    complete = true
                }
            }
        }

        val locListener = new LocationListener() {
            override def onLocationChanged(l: Location) {
                loc = bestLocation(loc, l)
                Log.d(C.TAG, "Best location: " + loc)
                if (complete)
                    finish.run()
            }
            override def onProviderDisabled(provider: String) {
                if (!gpsEnabled && !netEnabled) {
                    error = "All location providers have been disabled"
                    finish.run()
                }
            }
            override def onProviderEnabled(provider: String) = Unit
            override def onStatusChanged(
                    provider: String, status: Int, extras: Bundle) = Unit
        }

        var finished = false
        finish = () => {
            Log.i(C.TAG, "getLocation exiting, done: " + complete)
            handler.removeCallbacks(finish)
            if (gpsEnabled)
                lm.removeGpsStatusListener(gpsListener)

            lm.removeUpdates(locListener)
            report(replyTo, id, loc != null, complete, start, loc, error)
            finished = true
            stopSelf(startId)
        }

        handler.postDelayed(finish, 60 * 1000)

        if (!gpsEnabled && !netEnabled) {
            error = "No location providers have been enabled"
            finish.run()
            return
        }

        if (gpsEnabled) {
            lm.addGpsStatusListener(gpsListener)
            lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 0, 0.0f, locListener)
        }
        if (netEnabled) {
            lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 0, 0.0f, locListener)
        }
        Log.i(C.TAG, "Waiting for result from location listeners")
    }

    private def report(replyTo: String, id: String, success: Boolean,
            done: Boolean, start: Long, loc: Location, error: String) {
        val result = new JSONObject()

        if (error != null) {
            Log.e(C.TAG, error)
            result.put("error", error)
        }

        if (loc != null) {
            result.put("provider",  loc.getProvider())
            result.put("gpstime",   loc.getTime())
            result.put("latitude",  loc.getLatitude())
            result.put("longitude", loc.getLongitude())

            if (loc.hasAccuracy())
                result.put("accuracy", loc.getAccuracy())
        }
        
        result.put("gpsenabled",     gpsEnabled)
        result.put("networkenabled", netEnabled)
        result.put("gpsfix",         done)
        result.put("success",        success)
        result.put("time",           SystemClock.elapsedRealtime() - start)

        RecontrolrRegistrar.respond(replyTo, id, result.toString())
    }
}
