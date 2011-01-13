package com.hanhuy.android.c2dm.generic
import SetupActivity._
import android.accounts.AccountManager
import android.app.Activity
import android.content.{BroadcastReceiver, Intent, IntentFilter, Context}
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.Html
import android.util.Log
import android.view.View
import android.widget._
import AdapterView.OnItemClickListener

import com.google.android.c2dm.C2DMessaging

trait AccountNames { this: Context =>
    lazy val accountNames : Array[String] = {
        AccountManager.get(this).getAccounts().filter(
                (x) => x.`type` == "com.google").map(_.name)
    }
}
object SetupActivity {
    implicit def toBroadcastReceiver(f: (Context, Intent) => Unit) :
        BroadcastReceiver = new BroadcastReceiver() {
        def onReceive(c: Context, i: Intent) = f(c, i)
    }
    implicit def toOnClickListener(f: View => Unit) : View.OnClickListener =
            new View.OnClickListener() { def onClick(v: View) = f(v) }
    implicit def toOnItemClickListener(
            f: (AdapterView[_], View, Int, Long) => Unit) :
            OnItemClickListener = new OnItemClickListener() {
                def onItemClick(
                        av: AdapterView[_], v: View, pos: Int, id: Long) =
                            f(av, v, pos, id)
            }
}

class SetupActivity extends Activity with AccountNames {
    lazy val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    var contentId: Int = _

    val updateReceiver: BroadcastReceiver = (c: Context, i: Intent) => {
        var error = i.getStringExtra(C.EXTRA_ERROR)
        contentId match {
            case R.layout.intro     => {
                if (error != null) {
                    if (error == "SERVICE_NOT_AVAILABLE")
                        error = getString(R.string.service_unavailable)
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()

                    findView[Button](R.id.connect).setEnabled(true)
                    val bar = findView[ProgressBar](R.id.progress_bar)
                    bar.setVisibility(View.INVISIBLE)
                    val text = findView[TextView](R.id.connecting_text)
                    text.setVisibility(View.INVISIBLE)
                } else {
                    setContent(R.layout.connected)
                    Toast.makeText(this, getString(R.string.connected_text),
                            Toast.LENGTH_SHORT).show()
                }
            }
            case R.layout.connected => {
                if (error != null) {
                    if (error == "SERVICE_NOT_AVAILABLE")
                        error = getString(R.string.service_unavailable)
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()

                    findView[Button](R.id.disconnect).setEnabled(true)
                    val bar = findView[ProgressBar](R.id.progress_bar)
                    bar.setVisibility(View.INVISIBLE)
                    val text = findView[TextView](R.id.disconnecting_text)
                    text.setVisibility(View.INVISIBLE)
                } else {
                    setContent(R.layout.intro)
                    Toast.makeText(this, getString(R.string.disconnected_text),
                            Toast.LENGTH_SHORT).show()
                }
            }
        }
        ()
    }
    override protected def onCreate(state: Bundle) {
        super.onCreate(state)
        C2DMessaging.setBackoff(this, 1000)
        val regId = prefs.getString(C.REGISTRATION_KEY, null)
        setContent(if (regId != null) R.layout.connected else R.layout.intro)
        registerReceiver(updateReceiver, new IntentFilter(C.ACTION_UPDATE_UI),
                Manifest.permission.C2D_MESSAGE, null)
    }
    
    override def onDestroy() {
        super.onDestroy()
        unregisterReceiver(updateReceiver)
    }
    
    private def findView[T](id: Int) : T = findViewById(id).asInstanceOf[T]
    
    private def setContent(layout: Int) {
        contentId = layout
        setContentView(layout)
        layout match {
            case R.layout.intro      => setIntroView()
            case R.layout.no_account => setNoAccountView()
            case R.layout.connected  => setConnectedView()
        }
    }
    
    private def setConnectedView() {
        findView[Button](R.id.exit).setOnClickListener((v: View) => finish())
        val next = findView[Button](R.id.disconnect)
        val text = findView[TextView](R.id.account_names_text)
        text.setText(String.format(getString(R.string.account_names_text),
                prefs.getString(C.ACCOUNTS_KEY, "none")))
        next.setOnClickListener((v: View) => {
            next.setEnabled(false)
            val bar = findView[ProgressBar](R.id.progress_bar)
            bar.setVisibility(View.VISIBLE)
            val text = findView[TextView](R.id.disconnecting_text)
            text.setVisibility(View.VISIBLE)
            C2DMessaging.unregister(this)
        })
    }
    private def setNoAccountView() {
        findView[Button](R.id.exit).setOnClickListener((v: View) => finish())
        findView[Button](R.id.next).setEnabled(false)
    }

    private def setIntroView() {
        val exit = findView[Button](R.id.exit)
        val next = findView[Button](R.id.connect)
        val text = findView[TextView](R.id.intro_text)
        text.setText(Html.fromHtml(getString(R.string.intro_text)))
        exit.setOnClickListener((v: View) => finish())
        next.setOnClickListener((v: View) => {
            val names = accountNames
            if (names.length > 0) {
                next.setEnabled(false)
                val bar = findView[ProgressBar](R.id.progress_bar)
                bar.setVisibility(View.VISIBLE)
                val text = findView[TextView](R.id.connecting_text)
                text.setVisibility(View.VISIBLE)
                C2DMessaging.register(this, C.SENDER_ID)
            } else {
                setContent(R.layout.no_account)
            }
            ()
        })
    }
}
