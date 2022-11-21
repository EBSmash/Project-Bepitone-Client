package com.lambda.hud

import com.lambda.ExamplePlugin
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.plugin.api.PluginLabelHud
import com.lambda.modules.Breaker
//import com.lambda.modules.Breaker.pos
import com.lambda.modules.Breaker.x
import com.lambda.modules.Breaker.z

internal object StatusHud : PluginLabelHud(
    name = "BepitoneStatus",
    category = Category.MISC,
    description = "bep bep bep hud",
    pluginMain = ExamplePlugin
) {

    override fun SafeClientEvent.updateText() {
        displayText.addLine("State : ${Breaker.state}" )
        displayText.addLine("Going to $x $z", secondaryColor)
        displayText.addLine("ID: ${Breaker.id}", secondaryColor)
        displayText.addLine("Account: ${Breaker.username}", secondaryColor)
        displayText.addLine("Blocks Broken This session: ${Breaker.blocks_broken}", secondaryColor)

    }
}