package com.example.doomlauncher

import android.app.Activity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Folder khusus aplikasi, tidak perlu izin storage.
        val dir = File(getExternalFilesDir(null), "wads").apply { mkdirs() }

        val items = NativeBridge.scanWads(dir.absolutePath)
            .lines()
            .filter { it.isNotBlank() }

        val info = TextView(this).apply {
            text = "Taruh file WAD di:\n${dir.absolutePath}\n\n${items.size} file ditemukan."
            setPadding(32, 32, 32, 32)
        }

        val list = ListView(this)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        list.setOnItemClickListener { _, _, pos, _ ->
            Toast.makeText(this, items[pos], Toast.LENGTH_SHORT).show()
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(info)
            addView(list, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    }
}