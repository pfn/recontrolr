package com.hanhuy.android.c2dm.generic

object Conversions {
    implicit def toRunnable[A](f: () => A) : Runnable = new Runnable() {
        def run() = f()
    }
}
