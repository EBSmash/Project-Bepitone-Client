package com.lambda.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.modules.FingerLicker
import net.minecraft.util.math.BlockPos

object FingerLickerCommand : ClientCommand(
    name = "bepitone",
    description = "Finger Licker"
) {
    init {
        literal("start") {
            execute("Begin mining finger") {
                if (FingerLicker.isEnabled) {
                    if (FingerLicker.state == FingerLicker.MineState.ASSIGN) {
                        if (FingerLicker.state != FingerLicker.MineState.LOAD) {
                            FingerLicker.state = FingerLicker.MineState.TRAVEL
                        } else {
                            MessageSendHelper.sendChatMessage("Finger Licker is currently loading")
                        }
                    } else {
                        MessageSendHelper.sendChatMessage("Finger Licker must not be in use")
                    }
                } else {
                    MessageSendHelper.sendChatMessage("Finger Licker must be enabled")
                }
            }
        }
        literal("add") {
            execute("Add layer to finger licker") {
                if (FingerLicker.isEnabled) {
                    if (FingerLicker.state == FingerLicker.MineState.ASSIGN) {
                        if (FingerLicker.state != FingerLicker.MineState.LOAD) {
                            val pos = BlockPos(mc.player.posX.toInt(), 255, mc.player.posZ.toInt())
                            FingerLicker.requestNewFile(pos)
                        } else {
                            MessageSendHelper.sendChatMessage("Finger Licker is currently loading")
                        }
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