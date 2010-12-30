package com.hanhuy.android.c2dm.generic
import SetupActivity._
import android.accounts.AccountManager
import android.app.Activity
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.Html
import android.view.View
import android.widget._
import AdapterView.OnItemClickListener

import com.google.android.c2dm.C2DMessaging

object SetupActivity {
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

class SetupActivity extends Activity {
    lazy val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    override protected def onCreate(state: Bundle) {
        super.onCreate(state)

        val regId = prefs.getString(C.REGISTRATION_KEY, null)
        
        setContent(if (regId != null) R.layout.connected else R.layout.intro)
    }
    override def onPause() = super.onPause()
    override def onResume() = super.onResume()
    override def onDestroy() = super.onDestroy()
    
    private def findView[T](id: Int) : T = findViewById(id) match {
        case t: T => t
    }
    
    private def setContent(layout: Int) {
        setContentView(layout)
        layout match {
            case R.layout.intro => {
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
            case R.layout.no_account => {
                findView[Button](R.id.exit).setOnClickListener(
                        (v: View) => finish())
                findView[Button](R.id.next).setEnabled(false)
            }
            case R.layout.connected => {
            }
        }
    }
    
    private def accountNames : Array[String] = {
        AccountManager.get(this).getAccounts().filter(
                (x) => x.`type` == "com.google").map(_.name)
    }
}