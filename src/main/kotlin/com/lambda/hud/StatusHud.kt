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
        val assignment = Breaker.assignment;
        val layer = Breaker.assignment?.layer ?: 0 // TODO: this is a meaningless default <- [gay]

        displayText.addLine("Currently Working on Line: $layer")
        if (assignment != null && Breaker.breakState != null) {
            val breakSate = Breaker.breakState!!
            val totalDepth = assignment.baseDepth + assignment.data.size
            val currentDepth = assignment.baseDepth + breakSate.depth
            displayText.addLine("Depth: $currentDepth/$totalDepth")
        }
        displayText.addLine("Going to ${2 + (Breaker.xOffset + layer * 5)} ${z + Breaker.zOffset + negPosCheck(layer)}")
        displayText.addLine("Account: ${Breaker.username}")
        displayText.addLine("Blocks Broken This session: ${Breaker.blocksMinedTotal}", secondaryColor)
    }
    private fun negPosCheck(fileNum: Int): Int {
        if (fileNum % 2 == 0) {
            return 1
        }
        return -1
    }
}