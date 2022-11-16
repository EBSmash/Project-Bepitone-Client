package com.lambda.hud

import com.lambda.ExamplePlugin
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.plugin.api.PluginLabelHud
import com.lambda.modules.Breaker
import com.lambda.modules.Breaker.queue
import com.lambda.modules.Breaker.x
import com.lambda.modules.Breaker.z

internal object StatusHud : PluginLabelHud(
    name = "BepitoneStatus",
    category = Category.MISC,
    description = "Simple hud example",
    pluginMain = ExamplePlugin
) {

    override fun SafeClientEvent.updateText() {
        displayText.addLine(Breaker.state.toString())
        displayText.addLine("Going to $x $z", secondaryColor)
        displayText.addLine("Target: ${Breaker.queue.size}")
    }
}