package com.lambda.hud

import baritone.api.BaritoneAPI
import baritone.api.process.ICustomGoalProcess
import com.lambda.ExamplePlugin
import com.lambda.RoofGoal
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.plugin.api.PluginLabelHud
import com.lambda.modules.Breaker

internal object StatusHud : PluginLabelHud(
    name = "BepitoneStatus",
    category = Category.MISC,
    description = "bep bep bep hud",
    pluginMain = ExamplePlugin
) {

    override fun SafeClientEvent.updateText() {
        displayText.addLine("State: ${Breaker.state}" )
        val assignment = Breaker.assignment
        val breakState = Breaker.breakState
        val layer = Breaker.assignment?.layer ?: 0 // TODO: this is a meaningless default <- [gay]

        displayText.addLine("Currently Working on Line: $layer")
        if (assignment != null && breakState != null) {
            val totalDepth = assignment.baseDepth + assignment.data.size
            val currentDepth = assignment.baseDepth + breakState.depth
            displayText.addLine("Depth: $currentDepth/$totalDepth")
        }
        val goalProc: ICustomGoalProcess = BaritoneAPI.getProvider().primaryBaritone.customGoalProcess
        if (goalProc.isActive && Breaker.isEnabled) { // why is this crashing my shit babbaj? HMMMM?
            if (goalProc.goal is RoofGoal) {
                val goal = (goalProc.goal as RoofGoal).goal
                displayText.addLine("Going to ${goal.x} ${goal.z}")
            } else {
                displayText.addLine("Going to ${goalProc.goal}")
            }
        }

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