package com.hanhuy.android.c2dm.generic

object Conversions {
    implicit def toRunnable(f: () => Unit) : Runnable = new Runnable() {
        def run() = f()
    }
}
