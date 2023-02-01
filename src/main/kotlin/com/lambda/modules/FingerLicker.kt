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
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiDisconnected
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Deque
import java.util.LinkedList
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

    private var jobQueue : Deque<Deque<LinkedHashSet<BlockPos>>> = LinkedList()
    private var currentFile : ArrayList<LinkedHashSet<BlockPos>> = ArrayList()
    private val url = "alightintheendlessvoid.org"
    private val urlMain = "bep.babbaj.dev"
    private var currentFileNumber = 0
    private var finishedWithFile = false
//    private var state : CheckState = CheckState.REQUEST
    private var file = 0
    private var direction = 0

    private fun fileFromCoordinate () : Int {
        return 0
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
            if (Breaker.isEnabled) {
                MessageSendHelper.sendChatMessage("Please disable Bepitone Breaker before using finger licker.")
                disable()
            }
        }

        listener<ConnectionEvent.Disconnect> {
            disable()
        }

        listener<GuiEvent.Displayed> {
            (it.screen as? GuiDisconnected)?.let {
                disable()
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            val mc = Minecraft.getMinecraft()
            when (state) {
                CheckState.REQUEST -> {
                    finishedWithFile = false
                    requestNewFile()
                    state = CheckState.LOAD
                }
                CheckState.LOAD -> {
                    if (finishedWithFile) {
                        finishedWithFile = false
                        if (queue.isNotEmpty()) {
                            state = CheckState.TRAVEL
                        } else {
                            state = CheckState.REQUEST
                        }
                        MessageSendHelper.sendChatMessage("loaded file")
                    }
                }
                CheckState.TRAVEL -> {
                    val lastPos = queue.peekLast()
                    var z = 0
                    for (i in lastPos) {
                        z = i.z
                    }
                    BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto ${2 + (-5000 + file * 5)} 256 ${z -5000 + negPosCheck(file)}")
                    state = CheckState.CHECK
                }
                CheckState.CHECK -> {
                    if (!BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive) {
                        BaritoneAPI.getProvider().primaryBaritone.selectionManager.removeAllSelections()
                        var layer = queue.pollLast()
                        var incomplete = false
                        for (i in layer) {
                            if (mc.world.getBlockState(BlockPos(i.x - 5000, 255, i.z - 5000)).block !is BlockAir) {
                                incomplete = true
                            }
                            BaritoneAPI.getProvider().primaryBaritone.selectionManager.addSelection(BetterBlockPos(i.x - 5000, 255, i.z - 5000), BetterBlockPos(i.x - 5000, 255, i.z - 5000))
                        }
                        layer = queue.pollLast()
                        for (i in layer) {
                            if (mc.world.getBlockState(BlockPos(i.x - 5000, 255, i.z - 5000)).block !is BlockAir) {
                                incomplete = true
                            }
                            BaritoneAPI.getProvider().primaryBaritone.selectionManager.addSelection(BetterBlockPos(i.x - 5000, 255, i.z - 5000), BetterBlockPos(i.x - 5000, 255, i.z - 5000))
                        }
                        if (incomplete) {
                            logFailed(file)
                        } else {
                            finish()
                        }
                        state = CheckState.REQUEST
                    }
                }
            }
        }
        onDisable {
            updateLayer("$file")
        }
    }

    private fun updateLayer (fileNum : String) {
        if (state != CheckState.REQUEST) {
            val fileCopy = fileNum
            thread {
                try {
                    println("Running bepatone shutdown hook")

                    val url = URL("http://$url/update/${fileCopy}")

                    with(url.openConnection() as HttpURLConnection) {
                        requestMethod = "GET"  // optional default is GET

                        println("\nSent 'GET' request to URL : $url; Response Code : $responseCode")

                    }
                } catch (e: Exception) {
                    println("Running bepatone shutdown hook failed")
                }
            }
        }
    }

    enum class MineState {
        LOAD, TRAVEL, ASSIGN, MINE, QUEUE
    }

    private enum class CheckState {
        TRAVEL, CHECK, REQUEST, LOAD
    }
}
