package com.lambda.modules

import baritone.api.BaritoneAPI
import bep.SerializableBlockPos
import com.lambda.ExamplePlugin
import com.lambda.client.module.Category
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.Integer.parseInt
import java.net.ConnectException
import java.net.URL
import kotlin.collections.LinkedHashSet


internal object Breaker : PluginModule(name = "BepitoneBreaker", category = Category.MISC, description = "", pluginMain = ExamplePlugin) {
    var queue: LinkedHashSet<LinkedHashSet<SerializableBlockPos>> = LinkedHashSet()

    val threeTemp: LinkedHashSet<SerializableBlockPos> = LinkedHashSet();

    var x = 0
    var z = 0

    var busy = false;

    var empty = false;

    var state: State = State.ASSIGN

    var currentBreak = ""


    private val server by setting("Bepitone API Server IP", "Unchanged")

    init {

        onEnable {
            state = State.ASSIGN;
            busy = false
            empty = false
        }

        safeListener<TickEvent.ClientTickEvent> {

            when (state) {
                State.ASSIGN -> {
                    try {
                        val url = URL("http://localhost:8000/assign")
                        val connection = url.openConnection()
                        BufferedReader(InputStreamReader(connection.getInputStream())).use { inp ->
                            var line: String?
                            //for each line
                            while (inp.readLine().also { line = it } != null) {

                                if (line.toString() == "") {
                                    return@safeListener

                                } else {
                                    for (coordSet in line.toString().split("#")) {
                                        x = parseInt(coordSet.split(" ")[0])
                                        z = parseInt(coordSet.split(" ")[1])
                                        threeTemp.add(SerializableBlockPos(x, 255, z))
                                        currentBreak = SerializableBlockPos(x, 255, z).toString()
                                    }
                                    queue.add(threeTemp)
                                    threeTemp.clear()
                                }
                            }
                            state = State.TRAVEL
                        }


                    } catch (_: ConnectException) {
                        MessageSendHelper.sendErrorMessage("failed to connect to api \n Check that you set the ip. \n if you have Message EBS#2574.")
                        disable()
                    } catch (_: IOException) {
                        MessageSendHelper.sendChatMessage("Either Something went very wrong or WE FINSIHEDDD.")
                        disable()
                    }
                }
                State.TRAVEL -> {
                    if (queue.isEmpty()) {
                        state = State.ASSIGN
                        return@safeListener
                    }
                    if (!BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive) {
                        BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto $x 256 ${z + 3}")
                    }
                    state = State.BREAK

                }
                State.BREAK -> {
                    if (!BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive) {
                        if (queue.isNotEmpty()) {
                            val threeCoord = queue.iterator().next()
                            for (coord in threeCoord.iterator()) {
                                currentBreak = "${coord.toBlockPos()}";
                                mc.player.connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, coord.toBlockPos(), EnumFacing.UP))
                                mc.player.connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, coord.toBlockPos(), EnumFacing.UP))
                            }
                            queue.remove(threeCoord)
                            state = State.TRAVEL
                        } else {
                            state = State.ASSIGN
                            return@safeListener

                        }
                    }
                }
            }
        }
    }
}

enum class State() {
    ASSIGN, TRAVEL, BREAK
}





