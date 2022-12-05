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
        displayText.addLine("State: ${Breaker.state}" )
        displayText.addLine("Currently Working on Line: ${Breaker.file}")
        displayText.addLine("Going to ${2 + (Breaker.xOffset + Breaker.file * 5)} ${z + Breaker.zOffset + negPosCheck(Breaker.file)}")
        displayText.addLine("Account: ${Breaker.username}")
        displayText.addLine("Blocks Broken This session: ${Breaker.blocks_broken}", secondaryColor)
    }
    private fun negPosCheck(fileNum: Int): Int {
        if (fileNum % 2 == 0) {
            return 1
        }
        return -1
    }
}