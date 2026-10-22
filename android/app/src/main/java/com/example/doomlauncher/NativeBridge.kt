package com.example.doomlauncher

object NativeBridge {
    init {
        System.loadLibrary("launcher_jni")
    }

    @JvmStatic
    external fun scanWads(dir: String): String
}