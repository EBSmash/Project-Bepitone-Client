package com.lambda

import com.lambda.client.plugin.api.Plugin
import com.lambda.hud.StatusHud
import com.lambda.modules.Breaker
import com.lambda.modules.Scanner

internal object ExamplePlugin : Plugin() {

    override fun onLoad() {
        // Load any modules, commands, or HUD elements here
        modules.add(Breaker)
        modules.add(Scanner)
        hudElements.add(StatusHud)


    }

}