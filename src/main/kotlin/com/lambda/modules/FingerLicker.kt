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

    var listOfFiles = ArrayList<Int>()

    private var jobQueue : Queue<Deque<LinkedHashSet<BlockPos>>> = LinkedList()
    private var currentJob : Deque<LinkedHashSet<BlockPos>> = LinkedList()

    private var delayReconnect = 0
    private val url = "alightintheendlessvoid.org"
    private var finishedWithFile = false
    private var direction = 0
    private var breakPhase : BreakPhase = BreakPhase.SELECT
    private var firstBlock = true
    private var selections: ArrayList<LinkedHashSet<BlockPos>> = ArrayList(2)
    private fun fileFromCoordinate (x : Int) : Int {
        return x / 5
    }

    private enum class FingerCheck {
        AIR, OBBY, LOADING
    }

    fun requestNewFile(startingPosition : BlockPos) {
        if (state != MineState.ASSIGN) return
        thread {
            try {
                val url = URL("http://${url}/requestspecific/${direction}")
                var queue : Deque<LinkedHashSet<BlockPos>> = LinkedList()
                val connection = url.openConnection()
                BufferedReader(InputStreamReader(connection.getInputStream())).use { inp ->
                    var line: String?
                    var fileFirstLine = true
                    var indexOfStart = 0
                    var indexCounter = 0
                    while (inp.readLine().also { line = it } != null) {
                        if (fileFirstLine) {
                            fileFirstLine = false
                            listOfFiles.add(line.toString().toInt())
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
                                    if (blockPos == queueBuffer) indexOfStart = indexCounter
                                    queueBuffer.add(blockPos)
                                }
                                indexCounter++
                                queue.add(queueBuffer)
                            }
                        }
                    }
                    BaritoneAPI.getProvider().primaryBaritone.selectionManager.removeAllSelections()
                    val checkQueue = queue.toList()
                    // fuck simple for loops
                    var iterator : Int = indexOfStart
                    var checkEnum = FingerCheck.LOADING
                    var lastLine : LinkedHashSet<BlockPos>
                    lastLine = checkQueue[iterator]
                    var lastPosition = 0
                    var positionOfObby = 0
                    var positionOfAir = 0
                    while (checkEnum == FingerCheck.LOADING) {
                        var airBlockGate = Array(checkQueue[iterator].size) {false}
                        var gateIndex = 0
                        for (i in checkQueue[iterator]) {
                            if (mc.world.getBlockState(i).block is BlockAir) {
                                airBlockGate[gateIndex] = true
                            }
                            if (kotlin.math.abs(i.z - lastLine.first().z) > 1) {
                                checkEnum = FingerCheck.OBBY
                                positionOfObby = lastPosition
                            }
                            gateIndex++
                        }
                        if (airBlockGate.equals(true)) {
                            checkEnum = FingerCheck.AIR
                            positionOfAir = iterator
                        }
                        lastLine = checkQueue[iterator]
                        lastPosition = iterator
                        iterator++
                    }
                    lastLine = checkQueue[iterator]
                    lastPosition = 0
                    iterator = indexOfStart
                    if (checkEnum == FingerCheck.OBBY) {
                        var foundEnd = false
                        while (!foundEnd) {
                            val airBlockGate = Array(checkQueue[iterator].size) {false}
                            var gateIndex = 0
                            for (i in checkQueue[iterator]) {
                                if (mc.world.getBlockState(i).block is BlockAir) {
                                    airBlockGate[gateIndex] = true
                                }
                                gateIndex++
                            }
                            if (airBlockGate.equals(true)) {
                                positionOfAir = iterator
                                foundEnd = true
                            }
                            iterator++
                        }
                    } else {
                        var foundEnd = false
                        while (!foundEnd) {
                            val airBlockGate = Array(checkQueue[iterator].size) {false}
                            var gateIndex = 0
                            for (i in checkQueue[iterator]) {
                                if (mc.world.getBlockState(i).block is BlockAir) {
                                    airBlockGate[gateIndex] = true
                                }
                                if (kotlin.math.abs(i.z - lastLine.first().z) > 1) {
                                    positionOfObby = lastPosition
                                    foundEnd = true
                                }
                                gateIndex++
                            }
                            lastLine = checkQueue[iterator]
                            lastPosition = iterator
                            iterator++
                        }
                    }
                    val truncatedQueue : Deque<LinkedHashSet<BlockPos>> = LinkedList()
                    for (i in (checkQueue.slice(positionOfAir..positionOfObby).reversed())) {
                        truncatedQueue.add(i)
                    }
                    jobQueue.add(truncatedQueue)
                    while(truncatedQueue.isNotEmpty()) {
                        val queueLine = truncatedQueue.poll()
                        for (i in queueLine) {
                            val sel = BetterBlockPos(i.x, i.y, i.z)
                            BaritoneAPI.getProvider().primaryBaritone.selectionManager.addSelection(sel, sel)
                        }
                    }
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
            jobQueue.clear()
            listOfFiles.clear()
            state = MineState.ASSIGN
            breakPhase = BreakPhase.SELECT
            delayReconnect = 0
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

            if (listOfFiles.isEmpty()) {
                disable()
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
                    if (currentJob.isEmpty()) {
                        currentJob = jobQueue.poll()
                        state = MineState.MINE
                    } else {
                        state = MineState.MINE
                    }
                    firstBlock = true
                }

                MineState.MINE -> {
                    if (!BaritoneAPI.getProvider().primaryBaritone.builderProcess.isActive && !BaritoneAPI.getProvider().primaryBaritone.mineProcess.isActive && !BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive) {
                        if (breakPhase == BreakPhase.SELECT) {
                            if (currentJob.isEmpty()) {
                                state = MineState.TRAVEL
                                return@safeListener
                            }
                            val layer = currentJob.poll()
                            if (firstBlock) {
                                selections.add(layer)
                                selections.add(layer)
                                firstBlock = false
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
                                    BaritoneAPI.getProvider().primaryBaritone.selectionManager.addSelection(sel, sel)
                                }
                            }
                            for (coord in selections!![0]) {
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
                            if (needToMine || firstBlock || kotlin.math.abs(lastZ - currentZ) > 1) {
                                // if we can stand on the block we want to goto or we are are in a state where we can't assume we are close to the layer
                                if (mc.world.getBlockState(BlockPos(Breaker.X_OFFSET + currentMiddleX, 255 ,currentZ + Breaker.Z_OFFSET + negPosCheck(layer))) !is BlockAir || firstBlock || kotlin.math.abs(lastZ - currentZ) > 1) { // thanks leijurv papi
                                    firstBlock = false
                                    // if the chunk is loaded we can trust that it really is air in the selection so we can skip it
                                    val isRowLoaded = mc.world.isChunkGeneratedAt((currentMiddleX + Breaker.X_OFFSET) shr 4, (currentZ + Breaker.Z_OFFSET) shr 4)
                                    if (isRowLoaded && !needToMine) {
                                        return@safeListener
                                    }
                                    // if the row is in unloaded chunks and there isn't a gap between the previous, then the rest of the layer from what we can see is all air, and we will assume that the rest of the entire layer is air
                                    /*if (!isRowLoaded && kotlin.math.abs(lastZ - currentZ) == 1) {
                                        finishBreak()
                                        return@safeListener
                                    }*/
                                    goto(2 + (Breaker.X_OFFSET + layer * 5), currentZ + Breaker.Z_OFFSET + negPosCheck(layer))
                                }
                            }
                            breakPhase = BreakPhase.SET_AIR
                        } else if (breakPhase == BreakPhase.SET_AIR) {
                            BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("sel set air")
                            breakPhase = BreakPhase.WAIT
                        } else {
                            val packetsSayWeGood = selections!!.any { sel -> packetAirBlocks.containsAll(sel.map { pos -> BlockPos(pos.x + X_OFFSET, pos.y, pos.z + Z_OFFSET) }) }
                            // breakCounter 2 and else are for checking ghost blocks
                            if (breakPhase == BreakPhase.WAIT && waitDelay != 22 && !packetsSayWeGood) {
                                waitDelay++
                            } else {
                                packetAirBlocks.clear()
                                waitDelay = 0
                                breakPhase = BreakPhase.SELECT
                                BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("sel set air")
                                if (backupCounter >= 5) {
                                    sendUpdate()
                                    backupCounter = 0
                                } else {
                                    backupCounter++
                                }
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
                        if (mc.player.dimension == 0 && delayReconnect != 100) {
                            delayReconnect++
                        } else if (mc.player.dimension == 0 && delayReconnect == 100) {
                            state = MineState.TRAVEL
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
