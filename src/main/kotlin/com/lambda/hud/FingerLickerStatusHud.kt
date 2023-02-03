package com.lambda.hud

import com.lambda.ExamplePlugin
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.plugin.api.PluginLabelHud
import com.lambda.modules.FingerLicker

internal object FingerLickerStatusHud : PluginLabelHud(
    name = "FingerLickerStatus",
    category = Category.MISC,
    description = "uwu",
    pluginMain = ExamplePlugin
) {

    override fun SafeClientEvent.updateText() {
        displayText.addLine("State: ${FingerLicker.state}")
        displayText.addLine("Mine state: ${FingerLicker.breakPhase}")
    }
}
