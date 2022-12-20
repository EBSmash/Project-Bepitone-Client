package com.lambda.hud

import com.lambda.ExamplePlugin
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.plugin.api.PluginLabelHud
import com.lambda.modules.Breaker
import com.lambda.modules.Breaker.z

internal object StatusHud : PluginLabelHud(
    name = "BepitoneStatus",
    category = Category.MISC,
    description = "bep bep bep hud",
    pluginMain = ExamplePlugin
) {

    override fun SafeClientEvent.updateText() {
        displayText.addLine("State: ${Breaker.state}" )
        val layer = Breaker.assignment?.layer ?: 0 // TODO: this is a meaningless default
        val blocksBroken = Breaker.breakState?.blocksMined ?: 0
        displayText.addLine("Currently Working on Line: $layer")
        displayText.addLine("Going to ${2 + (Breaker.xOffset + layer * 5)} ${z + Breaker.zOffset + negPosCheck(layer)}")
        displayText.addLine("Account: ${Breaker.username}")
        displayText.addLine("Blocks Broken This session: $blocksBroken", secondaryColor)
    }
    private fun negPosCheck(fileNum: Int): Int {
        if (fileNum % 2 == 0) {
            return 1
        }
        return -1
    }
}