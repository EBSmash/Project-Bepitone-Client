package com.lambda.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.manager.managers.FriendManager
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.modules.FingerLicker
import net.minecraft.util.math.BlockPos

object FingerLickerCommand : ClientCommand(
    name = "bepitone",
    description = "Finger Licker"
) {
    init {
        literal("add") {
            execute("Add layer to finger licker") {
                if (FingerLicker.isEnabled) {
                    if (FingerLicker.state != FingerLicker.MineState.ASSIGN) {
                        val pos = BlockPos(mc.player.posX.toInt(), 255, mc.player.posZ.toInt())
                        FingerLicker.requestNewFile(pos)
                    } else {
                        MessageSendHelper.sendChatMessage("Finger Licker must not be in use")
                    }
                } else {
                    MessageSendHelper.sendChatMessage("Finger Licker must be enabled")
                }
            }
        }
    }
}