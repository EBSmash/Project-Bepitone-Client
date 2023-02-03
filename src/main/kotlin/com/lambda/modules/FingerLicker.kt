package com.lambda.modules

import baritone.api.BaritoneAPI
import baritone.api.utils.BetterBlockPos
import com.lambda.ExamplePlugin
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.GuiEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.module.Category
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.block.BlockAir
import net.minecraft.block.BlockLiquid
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiDisconnected
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fluids.IFluidBlock
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashSet
import kotlin.concurrent.thread

internal object FingerLicker : PluginModule(
    name = "BepitoneFingerLicker",
    category = Category.MISC,
    description = "ITSFINGERLICKINGOOD",
    pluginMain = ExamplePlugin
) {

    var state = MineState.ASSIGN
    var breakPhase : BreakPhase = BreakPhase.SELECT

    var listOfFiles : Queue<Int> = LinkedList()

    private var jobQueue : Queue<Deque<LinkedHashSet<BlockPos>>> = LinkedList()
    private var currentJob : Deque<LinkedHashSet<BlockPos>> = LinkedList()

    private var delayReconnect = 0
    private const val url = "alightintheendlessvoid.org"
    private var finishedWithFile = false
    private var firstBlock = true
    private var selections: ArrayList<LinkedHashSet<BlockPos>> = ArrayList(2)
    private var waitDelay = 0

    private fun fileFromCoordinate (x : Int) : Int {
        return x / 5
    }

    private enum class FingerCheck {
        AIR, OBBY, LOADING
    }

    fun requestNewFile(startingPosition : BlockPos) {
        if (state != MineState.ASSIGN) return
        if (listOfFiles.contains(fileFromCoordinate(startingPosition.x - Breaker.X_OFFSET))) {
            MessageSendHelper.sendChatMessage("Already queued this toe finger extremity")
            return
        }
        state = MineState.LOAD
        thread {
            try {
                val mc = Minecraft.getMinecraft()
                val url = URL("http://${url}/requestspecific/${fileFromCoordinate(startingPosition.x - Breaker.X_OFFSET)}")
                var queue : ArrayList<LinkedHashSet<BlockPos>> = ArrayList()
                val connection = url.openConnection()
                var file = 0
                BufferedReader(InputStreamReader(connection.getInputStream())).use { inp ->
                    var line: String?
                    var fileFirstLine = true
                    var indexOfStart = 0
                    var indexCounter = 0
                    while (inp.readLine().also { line = it } != null) {
                        if (fileFirstLine) {
                            fileFirstLine = false
                            file = line.toString().toInt()
                            listOfFiles.add(file)
                            MessageSendHelper.sendChatMessage("recieved ${listOfFiles.last()}")
                        } else {
                            if(line.toString() == "") {
                                return@use
                            } else {
                                val queueBuffer: LinkedHashSet<BlockPos> = LinkedHashSet()
                                for (coordBuffer in line.toString().split("#")) {
                                    val blockPos = BlockPos(
                                        coordBuffer.split(" ")[0].toInt(),
                                        255,
                                        coordBuffer.split(" ")[1].toInt())
                                    if (blockPos == BlockPos(startingPosition.x - Breaker.X_OFFSET, 255, startingPosition.z - Breaker.Z_OFFSET)) indexOfStart = indexCounter
                                    queueBuffer.add(blockPos)
                                }
                                indexCounter++
                                queue.add(queueBuffer)
                            }
                        }
                    }
                    BaritoneAPI.getProvider().primaryBaritone.selectionManager.removeAllSelections()
                    // fuck simple for loops
                    // also look, I know this is busted, I wrote it in one night fuck off
                    var iterator : Int = indexOfStart
                    var checkEnum = FingerCheck.LOADING
                    var lastPosition = indexOfStart
                    var positionOfObby = 0
                    var positionOfAir = 0
                    while (checkEnum == FingerCheck.LOADING) {
                        if (kotlin.math.abs(queue[iterator].first().z - queue[lastPosition].first().z) > 1) {
                            checkEnum = FingerCheck.OBBY
                            positionOfObby = lastPosition
                        }
                        lastPosition = iterator
                        iterator--
                    }
                    iterator = indexOfStart
                    var foundEnd = false
                    while (!foundEnd) {
                        var airBlockGate = 0
                        val airBlockGateTrue = 5
                        for (i in queue[iterator]) {
                            if (mc.world.getBlockState(BlockPos(i.x + Breaker.X_OFFSET, 255, i.z + Breaker.Z_OFFSET)).block is BlockAir) {
                                airBlockGate++
                            }
                        }
                        if (airBlockGate == airBlockGateTrue) {
                            positionOfAir = iterator
                            foundEnd = true
                        }
                        iterator ++
                    }
                    val truncatedQueue : Deque<LinkedHashSet<BlockPos>> = LinkedList()
                    var truncator : Int = positionOfAir
                    val finalTruncateThing : Int = positionOfObby
                    while (truncator >= finalTruncateThing) {
                        truncatedQueue.add(queue[truncator])
                        for (pos in queue[truncator]) {
                            val sel = BetterBlockPos(pos.x + Breaker.X_OFFSET, 255, pos.z + Breaker.Z_OFFSET)
                            BaritoneAPI.getProvider().primaryBaritone.selectionManager.addSelection(sel, sel)
                        }
                        truncator--
                    }
                    jobQueue.add(truncatedQueue)
                    finishedWithFile = true
                }
            } catch (_: ConnectException) {
                MessageSendHelper.sendChatMessage("Incorrect IP")
                disable()
            } catch (_: IOException) {
                MessageSendHelper.sendChatMessage("oopsy poopsy")
                disable()
            }
        }
    }

    private fun negPosCheck(fileNum : Int) :Int {
        if (fileNum % 2 == 0) {
            return 1
        }
        return 0
    }

    init {
        onEnable {
            state = MineState.ASSIGN
            breakPhase = BreakPhase.SELECT
            delayReconnect = 0
            if (mc.player.dimension == 1) state = MineState.QUEUE
            if (Breaker.isEnabled) {
                MessageSendHelper.sendChatMessage("Please disable Bepitone Breaker before using finger licker.")
                disable()
            }
        }

        listener<ConnectionEvent.Disconnect> {
            state = MineState.QUEUE
        }

        listener<GuiEvent.Displayed> {
            (it.screen as? GuiDisconnected)?.let {
                state = MineState.QUEUE
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            val mc = Minecraft.getMinecraft()

            if (state == MineState.ASSIGN) return@safeListener

            if (player.posY < 200 && state == MineState.MINE && mc.player.dimension == 0) { // if player falls
                try {
                    MessageSendHelper.sendChatMessage("Disabling because below y 200")
                    disable()
                    return@safeListener
                } catch (e: Exception) {
                    println("Running bepatone shutdown hook failed")
                }
            }

            if (state != MineState.QUEUE && mc.player.dimension == 1) {
                if (listOfFiles.isEmpty()) {
                    MessageSendHelper.sendChatMessage("Disabling because in queue with no previous assignment")
                    disable()
                    return@safeListener
                }
                state = MineState.QUEUE
                return@safeListener
            }

            when (state) {
                MineState.LOAD -> {
                    if (finishedWithFile) {
                        finishedWithFile = false
                        state = MineState.ASSIGN
                        return@safeListener
                    }
                }

                MineState.TRAVEL -> {
                    if (jobQueue.isNotEmpty()) {
                        currentJob = jobQueue.poll()
                        listOfFiles.poll()
                        state = MineState.MINE
                    } else {
                        MessageSendHelper.sendChatMessage("Please assign more jobs")
                        state = MineState.ASSIGN
                    }
                    firstBlock = true
                }

                MineState.MINE -> {
                    if (!BaritoneAPI.getProvider().primaryBaritone.builderProcess.isActive &&
                        !BaritoneAPI.getProvider().primaryBaritone.mineProcess.isActive &&
                        !BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive) {
                        if (breakPhase == BreakPhase.SELECT) {
                            if (currentJob.isEmpty()) {
                                state = MineState.TRAVEL
                                return@safeListener
                            }
                            val layer = currentJob.poll()
                            if (firstBlock) {
                                selections.clear()
                                selections.add(layer)
                                selections.add(layer)
                                firstBlock = false
                                BaritoneAPI.getProvider().primaryBaritone.selectionManager.removeAllSelections()
                            } else {
                                selections[1] = selections[0]
                                selections[0] = layer
                            }
                            BaritoneAPI.getProvider().primaryBaritone.selectionManager.removeAllSelections()
                            var needToMine = false
                            for (coord in selections[1]) {
                                val sel = BetterBlockPos(coord.x + Breaker.X_OFFSET, 255, coord.z + Breaker.Z_OFFSET)
                                if (mc.world.getBlockState(sel).block !is BlockLiquid &&
                                    mc.world.getBlockState(sel).block !is BlockAir &&
                                    mc.world.getBlockState(sel).block !is IFluidBlock) { // pretty much just "not water"
                                    needToMine = true
                                }
                                BaritoneAPI.getProvider().primaryBaritone.selectionManager.addSelection(sel, sel)
                            }
                            for (coord in selections[0]) {
                                val sel = BetterBlockPos(coord.x + Breaker.X_OFFSET, 255, coord.z + Breaker.Z_OFFSET)
                                if (mc.world.getBlockState(sel).block !is BlockLiquid &&
                                    mc.world.getBlockState(sel).block !is BlockAir &&
                                    mc.world.getBlockState(sel).block !is IFluidBlock) {
                                    needToMine = true
                                    BaritoneAPI.getProvider().primaryBaritone.selectionManager.addSelection(sel, sel)
                                }
                            }
                            val currentZ = layer.first().z
                            val currentMiddleX = 2 + (fileFromCoordinate(layer.first().x) * 5)
                            if (needToMine) {
                                goto(Breaker.X_OFFSET + currentMiddleX, currentZ + Breaker.Z_OFFSET + -1 * negPosCheck(fileFromCoordinate(layer.first().x)))
                            }
                            breakPhase = BreakPhase.SET_AIR
                        } else if (breakPhase == BreakPhase.SET_AIR) {
                            BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("sel set air")
                            breakPhase = BreakPhase.WAIT
                            waitDelay = 0
                        } else {
                            if (waitDelay != 22) {
                                waitDelay++
                            } else {
                                waitDelay = 0
                                breakPhase = BreakPhase.SELECT
                                BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("sel set air")
                            }
                        }
                    }
                }
                MineState.QUEUE -> {
                    // await joining server
                    val server = Minecraft.getMinecraft().currentServerData
                    if (server != null) {
                        if (!server.serverIP.contains("2b2t") && !server.serverIP.contains("proxy")) {
                            disable()
                            return@safeListener
                        }
                        if (mc.player.dimension == 0 && delayReconnect != 200) {
                            delayReconnect++
                        } else if (mc.player.dimension == 0 && delayReconnect == 200) {
                            state = MineState.MINE
                            delayReconnect = 0
                        }
                    }
                }
                else -> {
                    return@safeListener
                }
            }
        }

        onDisable {
            BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("stop")
            BaritoneAPI.getProvider().primaryBaritone.selectionManager.removeAllSelections()
        }
    }

    enum class MineState {
        LOAD, TRAVEL, ASSIGN, MINE, QUEUE
    }

    enum class BreakPhase {
        SELECT, // create the selections and then goto
        SET_AIR, // after goto, set air to start mining
        WAIT; // wait til server confirms blocks are broken or on the 1 second delay

        fun next(): BreakPhase {
            val constants = BreakPhase.values()
            return constants[(this.ordinal + 1) % constants.size]
        }
    }

    private fun goto(x: Int, z: Int) {
        BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto $x 256 $z")
    }
}
