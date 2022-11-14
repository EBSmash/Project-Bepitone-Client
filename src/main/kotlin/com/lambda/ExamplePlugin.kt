package com.lambda

import com.lambda.client.plugin.api.Plugin
import com.lambda.modules.Breaker


internal object ExamplePlugin : Plugin() {

    override fun onLoad() {
        // Load any modules, commands, or HUD elements here
        modules.add(Breaker)


    }

}