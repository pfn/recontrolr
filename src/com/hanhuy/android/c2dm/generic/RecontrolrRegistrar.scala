package com.hanhuy.android.c2dm.generic

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.provider.Settings

import android.os.Build
import android.telephony.TelephonyManager
import android.net.http.AndroidHttpClient
import android.util.Log

import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.message.BasicNameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity

import scala.collection.JavaConversions._
import scala.collection.mutable.Map
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import scala.collection.mutable.ArrayBuffer

object RecontrolrRegistrar {
    var _version: String = _
    var _versionName: String = _
    var _licenseVersion: String = "None"

    def init(c: Context) {
        if (_version != null) return

        val pm = c.getPackageManager()
        val info = pm.getPackageInfo("com.hanhuy.android.c2dm.generic",
                PackageManager.GET_SIGNATURES)
        try {
            val license = pm.getPackageInfo(
                    "com.hanhuy.android.c2dm.generic.license",
                    PackageManager.GET_SIGNATURES)
            if (info.signatures != null && license.signatures != null) {
                val set1 = new HashSet[Signature]()
                val set2 = new HashSet[Signature]()
                for (sig <- info.signatures)
                    set1 += sig
                for (sig <- license.signatures)
                    set2 += sig
                if (set1 == set2) {
                    _licenseVersion = "" + license.versionCode
                } else {
                    Log.w(C.TAG, "License signatures do not match!")
                }
            }
        }
        catch {
            case e: PackageManager.NameNotFoundException => {
                Log.i(C.TAG, "License not installed")
            }
        }
        _version = "" + info.versionCode
        _versionName = info.versionName
    }
    def register(c: Context, id: String, names: Array[String]) {
        val params = map
        val ts = c.getSystemService(
                Context.TELEPHONY_SERVICE).asInstanceOf[TelephonyManager]
        val androidID = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        if (ts != null) {
            val phone = ts.getLine1Number()
            val deviceid = ts.getDeviceId()
            params.put("phone_no", phone)
            if (deviceid != null)
                params.put("device_id", deviceid)
            else
                params.put("device_id", androidID)
        } else {
            params.put("device_id", androidID)
        }
        params.put("owner", names(0))
        params.put("managers", names.mkString(":"))
        params.put("c2dm_id", id)
        params.put("manuf", Build.MANUFACTURER)
        params.put("model", Build.MODEL)
        params.put("product", Build.PRODUCT)
        log(post(C.REGISTER_URL, params), "register")
    }

    def unregister(id: String) {
        val params = map
        params.put("c2dm_id", id)
        val response = post(C.UNREGISTER_URL, params)
        log(response, "unregister")
    }

    private def post(url: String, params: Map[String,String]) : HttpResponse = {
        val p = new HttpPost(url)
        val values = new ArrayBuffer[BasicNameValuePair]()
        for (k <- params.keys) {
            values += new BasicNameValuePair(k, params.getOrElse(k, null))
        }
        p.setHeader("Content-type", "application/x-www-form-urlencoded")
        p.setHeader("X-Recontrolr-Version-Code", _version)
        p.setHeader("X-Recontrolr-Version-Name", _versionName)
        p.setHeader("X-Recontrolr-License-Version", _licenseVersion)
        p.setEntity(new UrlEncodedFormEntity(values))
        val client = AndroidHttpClient.newInstance("Recontrolr/1.0")
        var response: HttpResponse = null
        try {
            response = client.execute(p)
        } catch {
            case e: Exception => Log.e(C.TAG, "Unable to respond", e)
        } finally {
            client.close()
        }
        response
    }

    def ack(url: String, id: String) {
        val params = map
        params.put("id", id)
        params.put("action", "ack")
        log(post(url, params), "ack")
    }

    def respond(url: String, id: String, response: String) {
        val params = map
        params.put("id", id)
        params.put("action", "done")
        params.put("response", response)
        Log.v(C.TAG, response)
        log(post(url, params), "respond")
    }

    private def log(r: HttpResponse, op: String) {
        if (r == null)
            return

        val status = r.getStatusLine()
        val code: java.lang.Integer = status.getStatusCode()
        Log.v(C.TAG, String.format("%s: %d %s",
                op, code, status.getReasonPhrase()))
    }

    private def map = new HashMap[String,String]()
}
