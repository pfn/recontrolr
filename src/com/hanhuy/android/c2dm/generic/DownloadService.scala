package com.hanhuy.android.c2dm.generic

import android.app.IntentService

import android.content.{Context, Intent}
import android.os.{Environment, PowerManager, SystemClock}
import android.net.http.AndroidHttpClient
import android.util.Log

import java.io.{File, FileOutputStream, InputStream}
import java.security.MessageDigest

import org.apache.http.HttpResponse
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.protocol.ClientContext
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.protocol.BasicHttpContext

import org.json.JSONObject

import DownloadService._

object DownloadService {
    var wlock: PowerManager#WakeLock = _
}

class DownloadService extends IntentService("DownloadService") {
    setIntentRedelivery(true)
    
    lazy val creds = new BasicCredentialsProvider()
    lazy val httpContext = {
        val c = new BasicHttpContext()
        c.setAttribute(ClientContext.CREDS_PROVIDER, creds)
        c
    }

    override def onHandleIntent(i: Intent) {
        if (wlock == null) {
            val pm = getSystemService(
                    Context.POWER_SERVICE).asInstanceOf[PowerManager]
            wlock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "DownloadService")
        }
        wlock.acquire()
        try {
            _onHandleIntent(i)
        } catch {
            case e: Exception => Log.e(C.TAG, "Could not download", e)
        } finally {
            wlock.release()
        }
    }
    def _onHandleIntent(i: Intent) {
        val u       = i.getStringExtra(C.PARAM_URL)
        val replyTo = i.getStringExtra(C.PARAM_REPLYTO)
        val id      = i.getStringExtra(C.PARAM_ID)
        val target  = i.getStringExtra(C.PARAM_TARGET)
        val user    = i.getStringExtra(C.PARAM_USER)
        val pass    = i.getStringExtra(C.PARAM_PASS)
        var length: Long = -1

        val doMD5 = i.hasExtra(C.PARAM_HASHMD5)
        val doSHA1 = i.hasExtra(C.PARAM_HASHSHA1)
        val start = SystemClock.elapsedRealtime()

        val sdext = if (i.hasExtra(C.PARAM_AUTOEXT))
                Environment.getExternalStorageDirectory() else null
        val f = new File(sdext, target)
        Log.d(C.TAG, "use auto storage path: " + i.hasExtra(C.PARAM_AUTOEXT))
        Log.d(C.TAG, "parent storage (optional): " + sdext)
        Log.i(C.TAG, "Download target: " + f)
        if (f.isDirectory()) {
            val m = target + ": is a directory, cannot download"
            report(replyTo, id, false, start, length, null, null, m)
            return
        }

        creds.clear()
        val client = AndroidHttpClient.newInstance("Recontrol/1.0")
        if (user != null || pass != null) {
            creds.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(user, pass))
        }

        var sha1str: String = null
        var md5str: String = null
        try {
            val get = new HttpGet(u)
            var response: HttpResponse = null
            try {
                response = client.execute(get, httpContext)
            } catch {
                case e: Exception => {
                    report(replyTo, id, false, start, length,
                            null, null, e.getMessage())
                    Log.e(C.TAG, "failed to query URL: " + u, e)
                    throw e
                }
            }
            val status = response.getStatusLine()
            val code = status.getStatusCode()
            try {
                if (code >= 200 && code < 300) {
                    val parent = f.getParentFile()
                    if (parent == null) {
                        val m = f + ": is in root directory, cannot write"
                        report(replyTo, id, false, start, length, null, null, m)
                        return
                    }
                    if (!parent.isDirectory()) {
                        if (parent.exists()) {
                            val m = parent.getPath() + ": parent not a dir"
                            report(replyTo, id, false, start, length,
                                    null, null, m)
                            return
                        }
                        if (!parent.mkdirs()) {
                            val m = parent.getPath() + ": can't to create dir"
                            report(replyTo, id, false, start, length,
                                    null, null, m)
                            return
                        }
                    }
                    var fout: FileOutputStream = null
                    var in: InputStream = null
                    var ex = false
                    try {
                        fout = new FileOutputStream(f, false)
                        val e = response.getEntity()
                        // TODO md5 and sha1 the content
                        var sha1: MessageDigest = null
                        if (doSHA1)
                            sha1 = MessageDigest.getInstance("SHA1")
                        var md5: MessageDigest = null
                        if (doMD5)
                            md5 = MessageDigest.getInstance("MD5")
                        in = e.getContent()
                        val buf = new Array[Byte](16384)
                        var read: Int = 0
                        read = in.read(buf, 0, 16384)
                        while (read != -1) {
                            if (doSHA1)
                                sha1.update(buf, 0, read)
                            if (doMD5)
                                md5.update(buf, 0, read)
                            fout.write(buf, 0, read)
                            read = in.read(buf, 0, 16384)
                        }
                        //e.writeTo(fout)
                        if (doMD5)
                            md5str  = md5.digest().map("%02x" format _).mkString
                        if (doSHA1)
                            sha1str = sha1.digest().map("%02x" format _).mkString
                    } catch {
                        case e: Exception => {
                            ex = true
                            report(replyTo, id, false, start,
                                    length, null, null, e.getMessage())
                            throw e
                        }
                    } finally {
                        if (fout != null)
                            fout.close()
                        if (!ex)
                            length = f.length()
                    }
                } else {
                    val m = "Unable to download: " + code + ": " +
                            status.getReasonPhrase()
                    report(replyTo, id, false, start, length, null, null, m)
                    return
                }
            } finally {
                response.getEntity().consumeContent()
            }
        } finally {
            client.close()
        }
        report(replyTo, id, true, start, length, md5str, sha1str, null)
    }
    
    private def report(replyTo: String, id: String,
            success: Boolean, start: Long, length: Long,
            md5: String, sha1: String, error: String) {
        val result = new JSONObject()
        result.put("success", success)
        if (!success) {
            Log.e(C.TAG, error)
            result.put("error", error)
        } else {
            if (sha1 != null)
                result.put("sha1", sha1)
            if (md5 != null)
                result.put("md5", md5)
        }
        result.put("time", SystemClock.elapsedRealtime() - start)
        if (length != -1)
            result.put("length", length)
        RecontrolrRegistrar.respond(replyTo, id, result.toString())
    }
}
