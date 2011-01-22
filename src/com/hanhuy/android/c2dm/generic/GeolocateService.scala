package com.hanhuy.android.c2dm.generic

import android.app.Service
import android.content.Context
import android.location.{Location, LocationManager, GpsStatus, LocationListener}
import android.os.{Bundle, Handler, HandlerThread, Looper, IBinder}
import android.os.{PowerManager, SystemClock}
import android.util.Log
import android.content.Intent
import PowerManager._
import org.json.JSONObject

import scala.collection.JavaConversions._
import scala.collection.mutable.HashMap

import Conversions._

import GeolocateService.wlock

object GeolocateService {
    private var wlock: PowerManager#WakeLock = _
}

class GeolocateService extends Service {
    var handler: Handler = _

    lazy val lm = getSystemService(
            Context.LOCATION_SERVICE).asInstanceOf[LocationManager]

    def gpsEnabled: Boolean =
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    def netEnabled: Boolean =
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    override def onBind(i: Intent) : IBinder = null

    override def onCreate() {
        super.onCreate()

        val t = new HandlerThread("GeolocateService")
        t.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            override def uncaughtException(t: Thread, ex: Throwable) {
                Log.e(C.TAG, "Uncaught exception in Handler", ex)
                if (wlock != null)
                    wlock.release()
            }
        })
        t.start()
        handler = new Handler(t.getLooper())
    }

    override def onDestroy() {
        handler.getLooper().quit()
    }

    override def onStartCommand(i: Intent, flags: Int, startId: Int) : Int = {
        GeolocateService.synchronized {
            if (wlock == null) {
                val pm = getSystemService(
                        Context.POWER_SERVICE).asInstanceOf[PowerManager]
                wlock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK, "GeolocateService")
            }
        }
        wlock.acquire()
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
                wlock.release()
                stopSelf(startId)
            }
        }
    }
    
    private def bestLocation(old1: Location, new1: Location) : Location = {
        if (old1 == null) return new1
        if (new1 == null) return old1

        val timeDiff = new1.getTime() - old1.getTime()
        val TWO_M = 2 * 60 * 1000
        val isMuchNewer = timeDiff >  TWO_M
        val isMuchOlder = timeDiff < -TWO_M
        val isNewer = timeDiff > 0

        if (isMuchNewer)
            return new1
        if (isMuchOlder)
            return old1

        if (old1.hasAccuracy() && !new1.hasAccuracy()) {
            Log.d(C.TAG, "old location has accuracy but new one does not")
            return old1
        }
        if (!old1.hasAccuracy() && new1.hasAccuracy()) {
            Log.d(C.TAG, "new location has accuracy but old one does not")
            return new1
        }
        if (old1.hasAccuracy() && new1.hasAccuracy()) {
            Log.d(C.TAG, "both locations have accuracy")
            val accuracyDiff = new1.getAccuracy() - old1.getAccuracy()
            val isLessAccurate = accuracyDiff > 0.0
            val isMoreAccurate = accuracyDiff < 0.0
            val isMuchLessAccurate = accuracyDiff > 200
            if (isMoreAccurate)
                return new1
            if (isNewer && !isLessAccurate)
                return new1
            val sameprovider = () => {
                val p1 = new1.getProvider()
                val p2 = old1.getProvider()

                if (p1 == null) p2 == null else p1 == p2
            }
            if (isNewer && !isMuchLessAccurate && sameprovider())
                return new1
        }

        return old1
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
                val e = l.getExtras()
                if (e != null) {
                    for (k <- e.keySet()) {
                        Log.d(C.TAG, l.getProvider() +
                                ": " + k + " => " + e.get(k))
                    }
                }
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
            Log.i(C.TAG, "getLocation finished, done: " + complete)
            handler.removeCallbacks(finish)
            if (gpsEnabled)
                lm.removeGpsStatusListener(gpsListener)

            lm.removeUpdates(locListener)
            report(replyTo, id, loc != null, complete, start, loc, error)
            finished = true
            wlock.release()
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

        val m = new HashMap[String,Any]()
        if (error != null) {
            Log.e(C.TAG, error)
            m ++= Map("error" -> error)
        }

        if (loc != null) {
            m ++= Map(
                "provider"  -> loc.getProvider(),
                "gpstime"   -> loc.getTime(),
                "latitude"  -> loc.getLatitude(),
                "longitude" -> loc.getLongitude()
            )

            if (loc.hasAccuracy())
                m ++= Map("accuracy" -> loc.getAccuracy())
            if (loc.hasAltitude())
                m ++= Map("altitude" -> loc.getAltitude())
            if (loc.hasSpeed())
                m ++= Map("speed" -> loc.getSpeed())
            if (loc.hasBearing())
                m ++= Map("speed" -> loc.getBearing())
        }
        
        m ++= Map(
            "gpsenabled"     -> gpsEnabled,
            "networkenabled" -> netEnabled,
            "gpsfix"         -> done,
            "success"        -> success,
            "time"           -> (SystemClock.elapsedRealtime() - start)
        )

        RecontrolrRegistrar.respond(replyTo, id, new JSONObject(m).toString())
    }
}
