package com.hanhuy.android.c2dm.generic

import android.content.Context
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
import scala.collection.mutable.ArrayBuffer

object RecontrolrRegistrar {
    def register(c: Context, id: String, names: Array[String]) {
        val params = map
        val ts = c.getSystemService(
                Context.TELEPHONY_SERVICE).asInstanceOf[TelephonyManager]
        if (ts != null) {
            val phone = ts.getLine1Number()
            val deviceid = ts.getDeviceId()
            params.put("phone_no", phone)
            params.put("device_id", deviceid)
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
        p.setEntity(new UrlEncodedFormEntity(values))
        val client = AndroidHttpClient.newInstance("Recontrolr/1.0")
        var response: HttpResponse = null
        try {
            response = client.execute(p)
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
        val status = r.getStatusLine()
        val code: java.lang.Integer = status.getStatusCode()
        Log.v(C.TAG, String.format("%s: %d %s",
                op, code, status.getReasonPhrase()))
    }

    private def map = new HashMap[String,String]()
}