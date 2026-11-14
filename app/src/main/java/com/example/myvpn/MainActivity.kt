package com.example.myvpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config

class MainActivity : AppCompatActivity() {

    private val backend by lazy { GoBackend(applicationContext) }

    private val tunnel = object : Tunnel {
        override fun getName() = "myvpn"
        override fun onStateChange(newState: Tunnel.State) {
            runOnUiThread { status.text = if (newState == Tunnel.State.UP) "Подключено" else "Отключено" }
        }
    }

    private lateinit var status: TextView
    private lateinit var configInput: EditText
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        status = TextView(this).apply {
            text = "Отключено"
            textSize = 22f
            gravity = Gravity.CENTER
        }
        configInput = EditText(this).apply {
            hint = "Вставь сюда WireGuard-конфиг ([Interface] ... [Peer] ...)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 10
            gravity = Gravity.TOP
            setText(getPreferences(MODE_PRIVATE).getString("config", ""))
        }
        button = Button(this).apply {
            text = "Подключить / отключить"
            setOnClickListener { onToggle() }
        }

        root.addView(status)
        root.addView(configInput, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(button)
        setContentView(root)
    }

    private fun onToggle() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, REQ_VPN)
        } else {
            toggle()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN && resultCode == Activity.RESULT_OK) toggle()
    }

    private fun toggle() {
        val text = configInput.text.toString()
        getPreferences(MODE_PRIVATE).edit().putString("config", text).apply()
        Thread {
            try {
                val current = backend.getState(tunnel)
                if (current == Tunnel.State.UP) {
                    backend.setState(tunnel, Tunnel.State.DOWN, null)
                } else {
                    val config = Config.parse(text.byteInputStream())
                    backend.setState(tunnel, Tunnel.State.UP, config)
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Ошибка: ${e.message}" }
            }
        }.start()
    }

    companion object {
        private const val REQ_VPN = 1
    }
}
