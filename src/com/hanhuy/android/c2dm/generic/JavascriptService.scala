package com.hanhuy.android.c2dm.generic

import android.app.IntentService


import android.content.{Context, Intent}
import android.os.{Environment, PowerManager, SystemClock}
import android.util.Log

import java.io.{File, FileReader, InputStreamReader, Reader}

import org.mozilla.javascript.Callable
import org.mozilla.javascript.{ContextFactory, Context => JSContext}
import org.mozilla.javascript.{Function => JSFunction}
import org.mozilla.javascript.{Script, Scriptable, ScriptableObject, UniqueTag}
import org.mozilla.javascript.{NativeJSON, LazilyLoadedCtor, ImporterTopLevel}
import org.mozilla.javascript.{NativeJavaTopPackage, NativeJavaPackage}

import org.json.{JSONArray, JSONObject, JSONTokener}

import Conversions._

import JavascriptService.wlock

class TimeoutJSContext extends JSContext(ContextFactory.getGlobal()) {
    var startTime = 0l
    // proguard is complaining that setInstructionObserverThreshold is missing
    def _setInstructionObserverThreshold(t: Int) {
        super.setInstructionObserverThreshold(t)
    }
}
/*
object TimeoutContextFactory {
}
*/
class TimeoutContextFactory extends ContextFactory {
    override def makeContext() : JSContext = {
        val c = new TimeoutJSContext()
        c._setInstructionObserverThreshold(10000)
        c
    }
    override def observeInstructionCount(c: JSContext, count: Int) {
        val ctx = c.asInstanceOf[TimeoutJSContext]
        val delta = System.currentTimeMillis() - ctx.startTime
        if (delta > 6 * 60 * 1000)
            throw new Error("Execution time exceeded");
    }

    override def doTopCall(ca: Callable, c: JSContext,
            scope: Scriptable, thisO: Scriptable,
            args: Array[Object]) : Object = {
        c.asInstanceOf[TimeoutJSContext].startTime = System.currentTimeMillis()
        super.doTopCall(ca, c, scope, thisO, args)
    }
}
object JavascriptService {
    var wlock: PowerManager#WakeLock = _
    ContextFactory.initGlobal(new TimeoutContextFactory())
}
class JavascriptService extends IntentService("JavascriptService") {
    setIntentRedelivery(true)

    lazy val parentScope = {
        var scope: Scriptable = null
        _usingJS((c: JSContext) => {
            //val s = c.initStandardObjects()
            val s = new ImporterTopLevel(c, true)
            JavaAdapter.init(c, s, false);
            ScriptableObject.putConstProperty(s, "context", this)
            val top = ScriptableObject.getProperty(
                    s, "Packages").asInstanceOf[NativeJavaTopPackage]
            val pkg = top.get("android", top)
            s.defineProperty("android", pkg, ScriptableObject.DONTENUM)
            // seems to throw an exception if used, don't lazy load
            // the android package namespace for now
            //new LazilyLoadedCtor(s, "android",
            //        "org.mozilla.javascript.NativeJavaTopPackage", false)
            scope = s
        })
        scope
    }

    private type Closeable = { def close(): Unit }
    private def usingIO[A](io: Closeable, f: () => A) {
        try {
            f()
        } finally {
            io.close()
        }
    }
    private def _usingJS[A](f: (JSContext) => A) {
        val ctx = JSContext.enter()
        ctx.setLanguageVersion(JSContext.VERSION_1_8)
        try {
            f(ctx)
        } finally {
            JSContext.exit()
        }
    }

    private def usingJS(f: (JSContext, Scriptable) => Object) : String = {
        var string: String = null
        _usingJS((c: JSContext) => {
            val scope = c.newObject(parentScope)
            scope.setPrototype(parentScope)
            scope.setParentScope(null)
            var result = f(c, scope)
            if (result != null)
                result = NativeJSON.stringify(
                        c, scope, result, null, null)
            if (result != null) {
                string = JSContext.toString(result)
            }
        })
        string
    }

    override def onHandleIntent(i: Intent) {
        JavascriptService.synchronized {
            if (wlock == null) {
                val pm = getSystemService(
                        Context.POWER_SERVICE).asInstanceOf[PowerManager]
                wlock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "JavascriptService")
            }
        }
        wlock.acquire()
        try {
            _onHandleIntent(i)
        } finally { // should never get an uncaught exception here
            wlock.release()
        }
    }
    private def _onHandleIntent(i: Intent) {
        val replyTo = i.getStringExtra(C.PARAM_REPLYTO)
        val id      = i.getStringExtra(C.PARAM_ID)

        val start = SystemClock.elapsedRealtime()
        val o = new JSONObject()
        o.put("success", true)
        val parent = if (i.hasExtra(C.PARAM_AUTOEXT))
                Environment.getExternalStorageDirectory() else null
        val js = i.getStringExtra(C.PARAM_TARGET)
        try {
            if (js == null) {
                Log.e(C.TAG, "JS target not specified")
                o.put("success", false)
                o.put("error", "No JS target set")
            } else {
                val file = new File(parent, js)
                if (js.toLowerCase().endsWith(".js") && file.isFile()) {
                    val r = new FileReader(file)
                    val header = "(function() {\n"
                    val footer = "\n})();\n"
                    usingIO(r, () => {
                        // wrap the reader in a closure for convenience
                        val wrappedReader = new Reader() {
                            override def close() = Unit
                            var headerDone = false
                            var footerDone = false
                            var streamDone = false;
                            override def read(
                                    buf: Array[Char], off: Int, len: Int) : Int = {
                                if (!headerDone) {
                                    val hlen = header.length
                                    if (hlen > len) {
                                        // shouldn't ever happen, who sets
                                        // read sizes under 20 bytes??
                                        throw new IllegalStateException(
                                                "hlen > len")
                                    }
                                    val b = header.toCharArray()
                                    System.arraycopy(b, 0, buf, off, hlen)
                                    headerDone = true
                                    return hlen
                                }
                                
                                var c: Int = -1
                                if (!streamDone)
                                    c = r.read(buf, off, len)
                                if (c == -1)
                                    streamDone = true
                                else {
                                    return c
                                }

                                if (streamDone && !footerDone) {
                                    val flen = footer.length
                                    if (flen > len)
                                        throw new IllegalStateException("flen > len")
                                    val b = footer.toCharArray()
                                    System.arraycopy(b, 0, buf, off, flen)
                                    footerDone = true
                                    return flen
                                }
                                -1
                            }
                        }
                        val result = usingJS((c: JSContext, s: Scriptable) => {
                            c.evaluateReader(s, wrappedReader, js, 0, null)
                        })
                        Log.i(C.TAG, "JS result: " + result)
                        if (result != null) {
                            val c = result.trim().charAt(0);
                            if (c == '{') // } dumb eclipse
                                o.put("result", new JSONObject(result))
                            else if (c == '[')
                                o.put("result", new JSONArray(result))
                            else if (c == '"') {
                                // unescape any escaped strings
                                val t = new JSONTokener(result)
                                t.nextString('"')
                                o.put("result", t.nextString('"'))
                            }
                            else
                                o.put("result", result)
                        }
                    })
                } else {
                    if (!file.exists())
                        o.put("error", "Target does not exist: " + js)
                    else
                        o.put("error", "Target is not a js file: " + js)
                    o.put("success", false)
                }
            }
        } catch {
            case e: Exception => {
                Log.e(C.TAG, "Failed executing script", e)
                o.put("error", e.getMessage())
                o.put("success", false)
            }
        } finally {
            if (i.hasExtra(C.PARAM_DELETE) && js != null) {
                new File(parent, js).delete()
            }
            o.put("time", SystemClock.elapsedRealtime() - start)
            RecontrolrRegistrar.respond(replyTo, id, o.toString())
        }
    }
}
