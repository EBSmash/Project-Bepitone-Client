package com.lambda

import com.lambda.client.plugin.api.Plugin
import com.lambda.commands.FingerLickerCommand
import com.lambda.hud.StatusHud
import com.lambda.modules.Breaker
import com.lambda.modules.FingerLicker
//import com.lambda.modules.FingerLicker
import com.lambda.modules.Scanner

internal object ExamplePlugin : Plugin() {

    override fun onLoad() {
        // Load any modules, commands, or HUD elements here
        modules.add(Breaker)
        modules.add(Scanner)
        modules.add(FingerLicker)
        hudElements.add(StatusHud)
        commands.add(FingerLickerCommand)
    }
}