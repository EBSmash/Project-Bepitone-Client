package com.lambda.modules

import com.lambda.ExamplePlugin
import com.lambda.client.module.Category
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.util.text.MessageSendHelper

internal object Scanner : PluginModule(
    name = "Bepitone Scanner",
    category = Category.MISC,
    description = "X3 owo",
    pluginMain = ExamplePlugin
) {

    init {
        onEnable {
            MessageSendHelper.sendChatMessage("YOU CANNOT GET DIS")
            disable()
        }
        onDisable {
        }
    }
}
