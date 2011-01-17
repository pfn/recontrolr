package com.hanhuy.android.c2dm.generic

import SetupActivity._

import android.accounts.AccountManager
import android.app.{Activity, AlertDialog, Dialog}
import android.content.{BroadcastReceiver, Intent, IntentFilter, Context}
import android.content.{ComponentName, DialogInterface}
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.Html
import android.util.Log
import android.view.{View, Menu, MenuInflater, MenuItem}
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
    implicit def toBroadcastReceiver[A](f: (Context, Intent) => A) :
        BroadcastReceiver = new BroadcastReceiver() {
        def onReceive(c: Context, i: Intent) = f(c, i)
    }
    implicit def toOnClickListener[A](f: View => A) : View.OnClickListener =
            new View.OnClickListener() { def onClick(v: View) = f(v) }
    implicit def toDialogInterfaceOnClickListener[A](
            f: (DialogInterface, Int) => A) :
                DialogInterface.OnClickListener = {
        new DialogInterface.OnClickListener() {
            def onClick(d: DialogInterface, id: Int) = f(d, id)
        }
    }
    implicit def toOnItemClickListener[A](
            f: (AdapterView[_], View, Int, Long) => A) :
            OnItemClickListener = new OnItemClickListener() {
                def onItemClick(
                        av: AdapterView[_], v: View, pos: Int, id: Long) =
                            f(av, v, pos, id)
            }
}

class SetupActivity extends Activity with AccountNames {
    lazy val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    var contentId: Int = _
    private val STEALTH_MODE_PROMPT = 1
    private val STEALTH_MODE_ERROR  = 2

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
    }
    override protected def onCreate(state: Bundle) {
        super.onCreate(state)
        C2DMessaging.setBackoff(this, 1000)
        val regId = prefs.getString(C.REGISTRATION_KEY, null)
        setContent(if (regId != null) R.layout.connected else R.layout.intro)
        registerReceiver(updateReceiver, new IntentFilter(C.ACTION_UPDATE_UI),
                Manifest.permission.C2D_MESSAGE, null)
                
                            val pm = getPackageManager()
                            val name = new ComponentName(
                                    SetupActivity.this, ".SetupActivityAlias")
                            val v = pm.getComponentEnabledSetting(name)
                            Log.i(C.TAG, name + ": component enabled: " + v)
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
        })
    }

    override def onCreateOptionsMenu(menu: Menu) : Boolean = {
        val inflater = getMenuInflater();
        inflater.inflate(R.menu.options, menu)
        true
    }
    
    override def onOptionsItemSelected(item: MenuItem) : Boolean = {
        if (item.getItemId() == R.id.stealth_mode) {
            if (contentId == R.layout.connected) {
                showDialog(STEALTH_MODE_PROMPT)
            } else {
                showDialog(STEALTH_MODE_ERROR)
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    override def onCreateDialog(id: Int) : Dialog = {
        val builder = new AlertDialog.Builder(this)
        builder.setCancelable(false)
        id match {
            case STEALTH_MODE_PROMPT => {
                builder.setMessage(getString(R.string.stealth_mode_prompt))
                builder.setPositiveButton(getString(R.string.yes),
                        (d: DialogInterface, id: Int) => {
                            val pm = getPackageManager()
                            val name = new ComponentName(
                                    SetupActivity.this, ".SetupActivityAlias")
                            pm.setComponentEnabledSetting(name,
                                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                    0)
                            Toast.makeText(SetupActivity.this,
                                    getString(R.string.stealth_mode_enabled),
                                    Toast.LENGTH_SHORT).show()
                            d.dismiss()
                        })
                builder.setNegativeButton(getString(R.string.no),
                        (d: DialogInterface, id: Int) => {
                            d.cancel()
                        })
            }
            case STEALTH_MODE_ERROR  => {
                builder.setMessage(getString(R.string.stealth_mode_error))
                builder.setNegativeButton(getString(R.string.back),
                        (d: DialogInterface, id: Int) => {
                            d.cancel()
                        })
            }
        }
        builder.create()
    }
}
