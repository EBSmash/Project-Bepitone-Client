package com.lambda.modules

import baritone.api.BaritoneAPI
<<<<<<< Updated upstream
import com.beputils.Timer
import com.lambda.ExamplePlugin
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.module.Category
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getHitVec
import net.minecraft.init.Blocks
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
=======
import bep.SerializableBlockPos
import com.lambda.ExamplePlugin
import com.lambda.client.module.Category
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.util.EnumFacing
>>>>>>> Stashed changes
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.Integer.parseInt
import java.net.ConnectException
import java.net.URL
<<<<<<< Updated upstream
import java.util.*
=======
import kotlin.collections.LinkedHashSet
>>>>>>> Stashed changes


internal object Breaker : PluginModule(name = "BepitoneBreaker", category = Category.MISC, description = "", pluginMain = ExamplePlugin) {
    var queue: LinkedHashSet<LinkedHashSet<SerializableBlockPos>> = LinkedHashSet()

<<<<<<< Updated upstream
    private val miningTimer = Timer()

    private var lastHitVec: Vec3d? = null
=======
    val threeTemp: LinkedHashSet<SerializableBlockPos> = LinkedHashSet();
>>>>>>> Stashed changes

    var x = 0
    var z = 0

<<<<<<< Updated upstream
    var sent = false
=======
    var busy = false;
>>>>>>> Stashed changes

    var empty = false;

    var state: State = State.ASSIGN

    var currentBreak = ""

    var currentlyBreaking = false;

    private val server by setting("Bepitone API Server IP", "Unchanged")

    init {

        onEnable {
            state = State.ASSIGN;
            busy = false
            empty = false
        }

        safeListener<TickEvent.ClientTickEvent> {
<<<<<<< Updated upstream
            when (state) {
                0 ->
                    //sent get req
                    try {
                        val url = URL("http://2.tcp.ngrok.io:17605/assign")
                        val connection = url.openConnection()
                        BufferedReader(InputStreamReader(connection.getInputStream())).use { inp ->
                            var line: String?
                            //for each line
                            while (inp.readLine().also { line = it } != null) {
                                //debug print
//                        MessageSendHelper.sendChatMessage(line.toString())

                                if (line.toString() == "") {
                                    empty = true;
                                    break
                                } else {
                                    empty = false;
                                }

                                if (!empty) {
                                    queue.add(line.toString())
                                }
                            }
                            if (empty) {
                                state = 0
                                return@safeListener
                            } else {
                                state = 1
=======

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
>>>>>>> Stashed changes
                            }
                            state = State.TRAVEL
                        }
<<<<<<< Updated upstream
                    } catch (_: ConnectException) {
                        MessageSendHelper.sendErrorMessage("failed to connect to api \n Check that you set the ip. \n if you have Message EBS#2574.")
                        disable()
                    } catch (_: IOException) {
                        MessageSendHelper.sendChatMessage("Either Something went very wrong or WE FINSIHEDDD.")
                        disable()
                    }

                1 ->
                    if (!queue.isEmpty()) {

                        val line = queue.poll();

                        if (line == null) {
                            state = 0
                            return@safeListener
                        }

                        val list = line.split("#")

                        try {
                            val coord1 = BlockPos(parseInt(list[0].split(" ")[0]), 255, parseInt(list[0].split(" ")[1]))
                            toBreak.add(coord1)

                        } catch (_: IndexOutOfBoundsException) {

                        }

                        try {
                            val coord2 = BlockPos(parseInt(list[1].split(" ")[0]), 255, parseInt(list[1].split(" ")[1]))
                            toBreak.add(coord2)

                            x = coord2.x
                            z = coord2.z
                        } catch (_: IndexOutOfBoundsException) {

                        }

                        try {
                            val coord3 = BlockPos(parseInt(list[2].split(" ")[0]), 255, parseInt(list[2].split(" ")[1]))
                            toBreak.add(coord3)
                        } catch (_: IndexOutOfBoundsException) {

                        }
                        if (mc.world.getBlockState(BlockPos(x, 255, z)).block == Blocks.AIR) {
                            state = 0
                            return@safeListener
                        }
                        if (!BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive) {
                            BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto $x 256 ${z + 3})")
                            if (!BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive) {
                                for (pos in toBreak) {
                                    if (!currentlyBreaking) {
                                        currentlyBreaking = true;
                                        MessageSendHelper.sendChatMessage("started mining")

                                        lastHitVec = getHitVec(pos, EnumFacing.UP)

                                        val rotation = lastHitVec?.let { getRotationTo(it) }

                                        sendPlayerPacket() {
                                            if (rotation != null) {
                                                rotate(rotation)
                                            }
                                        }
                                        mc.player.connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, pos, EnumFacing.UP))
                                        miningTimer.reset()
                                    }

                                    if (miningTimer.hasReached(2650)) {
                                        val rotation = lastHitVec?.let { getRotationTo(it) }

                                        sendPlayerPacket() {
                                            if (rotation != null) {
                                                rotate(rotation)
                                            }
                                        }

                                        MessageSendHelper.sendChatMessage("Trying to finish mining")
                                        mc.player.connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, EnumFacing.UP))

                                        miningTimer.reset()
                                        currentlyBreaking = false;
                                        sent = true
                                        state = 0
                                    }
                                }
                            }
                        }
                    }
            }
        }

    }
}
=======


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




>>>>>>> Stashed changes

