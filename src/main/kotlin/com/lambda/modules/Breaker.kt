package com.lambda.modules

import baritone.api.BaritoneAPI
import com.lambda.ExamplePlugin
import com.lambda.client.module.Category
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.Integer.parseInt
import java.net.ConnectException
import java.net.URL
import java.util.LinkedList
import java.util.Queue
import kotlin.collections.LinkedHashSet


internal object Breaker : PluginModule(name = "BepitoneBreaker", category = Category.MISC, description = "", pluginMain = ExamplePlugin) {
    var queue: Queue<LinkedHashSet<BlockPos>> = LinkedList()

    private var threeTemp: LinkedHashSet<BlockPos> = LinkedHashSet();

    private var hasInitialized = false

    var x = 0
    var z = 0

    private var busy = false;

    private var empty = false;

    var state: State = State.ASSIGN

    private var threeCoord: LinkedHashSet<BlockPos> = LinkedHashSet();

    var timer: TickTimer = TickTimer(TimeUnit.MILLISECONDS)

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
                                    return@use

                                } else {
                                    //17 142
                                    for (coordSet in line.toString().split("#")) {
                                        //17
                                        x = parseInt(coordSet.split(" ")[0])
                                        //142
                                        z = parseInt(coordSet.split(" ")[1])
                                        //{{BlockPos(17,142)}, {BlockPos(17 141)},{BlockPos(17 140)}}
                                        threeTemp.add(BlockPos(x, 255, z))
                                    }

//                                    MessageSendHelper.sendChatMessage(threeTemp.toString())
                                    //{{{BlockPos(17,142)}, {BlockPos(17 141)},{BlockPos(17 140)}}}
                                    queue.add(threeTemp)
//                                    MessageSendHelper.sendChatMessage(threeTemp.toString())

                                }
                            }
                            state = State.TRAVEL
                        }


                    } catch (_: ConnectException) {
                        MessageSendHelper.sendErrorMessage("failed to connect to api \n Check that you set the ip. \n if you have Message EBS#2574.")
                        disable()
                    } catch (_: IOException) {
                        MessageSendHelper.sendChatMessage("Either Something went very wrong or WE FINSIHEDDD (x to doubt).")
                        disable()
                    }
                }


                State.TRAVEL -> {
                    if (queue.isEmpty()) {
                        state = State.ASSIGN
                        MessageSendHelper.sendChatMessage("Task Queue is empty, requesting more assignments")
                    }
                    if (!BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive && !queue.isEmpty()) {
                        MessageSendHelper.sendChatMessage("Traveling")
                        BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto $x 256 ${z - 2}")
                        state = State.BREAK
                    }
                }

                State.BREAK -> {
                    if (!busy) {
                        threeCoord = queue.poll()
                        if (threeCoord.isEmpty()) {
                            MessageSendHelper.sendChatMessage("empty")

                            state = State.ASSIGN
                            return@safeListener
                        }

                        for (coord in threeCoord) {

                            mc.player.swingArm(EnumHand.MAIN_HAND)

                            x = coord.x
                            z = coord.z

                            mc.player.connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, coord, EnumFacing.UP))

                            if (timer.tick(1500)) {
                                mc.player.connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, coord, EnumFacing.UP))
                            }


                        }
                        BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto $x 256 $z")

                        if (!BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive) {
                            busy = true
                        }

                    } else {
                        state = State.TRAVEL

                    }

                }
            }
        }
    }


    fun notBusy(): Boolean {
        var notBusy = !BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive

        if (timer.tick(150)) {
            notBusy = !BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive
        }
        return notBusy
    }
}


enum class State() {
    ASSIGN, TRAVEL, BREAK
}