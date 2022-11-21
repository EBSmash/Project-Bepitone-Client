package com.lambda

import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.ShutdownEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.plugin.api.Plugin
import com.lambda.client.util.threads.safeListener
import com.lambda.hud.StatusHud
import com.lambda.modules.Breaker
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL


internal object ExamplePlugin : Plugin() {

    override fun onLoad() {
        // Load any modules, commands, or HUD elements here
        modules.add(Breaker)
        hudElements.add(StatusHud)


    }

}