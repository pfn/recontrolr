package com.hanhuy.android.c2dm.generic

import android.app.IntentService
import android.content.Context

import android.location._
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.content.Intent

import Conversions._

class GeolocateService extends IntentService("GeolocateService") {
    setIntentRedelivery(true)

    override def onHandleIntent(i: Intent) {
        
        Log.i(C.TAG, "Processing geolocate request")
        val lm = getSystemService(
                Context.LOCATION_SERVICE).asInstanceOf[LocationManager]
        val criteria = new Criteria()
        criteria.setAccuracy(Criteria.ACCURACY_FINE)
        val provider = lm.getBestProvider(criteria, true)

        var l: Location = null
        l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        Log.i(C.TAG, "L: " + l)
        l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        Log.i(C.TAG, "L: " + l)
        l = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        Log.i(C.TAG, "L: " + l)
        
        Log.i(C.TAG, "Requesting location update")
        lm.requestLocationUpdates(provider, 0, 0.0f,
                new LocationListener() {
            var bestLocation: Location = _
            def onLocationChanged(l: Location) {
                Log.i(C.TAG, "Location changed " + l.getLongitude() + ", " + l.getLatitude())
                if (bestLocation == null) {
                    bestLocation = l
                    return
                }
                if (l.hasAccuracy()) {
                    val delta = l.getAccuracy() - bestLocation.getAccuracy()
                    if (delta < 0.0) {
                        
                    }
                } else {
                }
                stopListener()
            }
            def onProviderDisabled(p: String) {
                Log.e(C.TAG, "LocationProvider disabled")
                stopListener()
            }
            def onProviderEnabled(p: String) = Unit
            def onStatusChanged(p: String, s: Int, extras: Bundle) {
                if (s == LocationProvider.OUT_OF_SERVICE) {
                    Log.e(C.TAG, "LocationProvider out of service!")
                    stopListener()
                }
            }
            private def stopListener() {
                lm.removeUpdates(this)
            }
        }, Looper.myLooper())
    }
}
