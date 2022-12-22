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
import net.minecraft.block.BlockObsidian
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
import java.util.concurrent.Executors
import kotlin.collections.LinkedHashSet


internal object Breaker : PluginModule(
        name = "BepitoneBreaker",
        category = Category.MISC,
        description = "",
        pluginMain = ExamplePlugin
    ) {
    private val EXECUTOR = Executors.newCachedThreadPool()
    // the int is the index in the layer data returned by the api
    private var queue: Deque<Pair<LinkedHashSet<BlockPos>, Int>> = LinkedList() // in the future this should be a double ended queue which instead of snaking with files just snakes in the client
    private var brokenBlocksBuf = 0
    private var failedLayerCounter = 0
    private var delay = 0
    private var firstLineOfFailedFile : BlockPos = BlockPos(0,0,0)
    private var loadChunkCount = 15
    private var backupCounter = 20
    private var delayReconnect = 0
    var breakState: BreakState? = null
    private var breakCounter = 0 // I don't like this
    var x = 0
    var z = 0
    var assignment: Assignment? = null
    private var busy = false
    private var empty = false
    private var queueSizeDesired = 0
    private var failurePosition = 0
    const val xOffset = -5000
    const val zOffset = -5000
    var username: String? = null
    private val url by setting("Server IP", "alightintheendlessvoid.org")
    private var runShutdownOnDisable = true
    private var sel = BetterBlockPos(0,0,0)
    var state: State = State.ASSIGN
    private var firstBlock  = true
    private var threeCoord: LinkedHashSet<BlockPos> = LinkedHashSet()
    private var selections: ArrayList<LinkedHashSet<BlockPos>> = ArrayList(2)

    class BreakState {
        var blocksMined = 0
        var depth = 0 // TODO: this needs to be set
        var depthOfLastUpdate = 0
    }

    class Assignment(val layer: Int, val isFail: Boolean, val data: List<List<BlockPos>>)

    private fun doApiCall(path: String, method: String): String? {
        val url = URL("http://$url/$path")
        try {
            val con = url.openConnection() as HttpURLConnection
            con.requestMethod = method
            val responseCode = con.getResponseCode()
            val reader = BufferedReader(InputStreamReader(con.getInputStream()))
            val text = reader.readText()
            if (responseCode in 200..299) {
                return text
            }
            MessageSendHelper.sendChatMessage("Api call to $path returned an error ($responseCode):")
            MessageSendHelper.sendChatMessage(text)
            println(text)
            return null
        } catch (ex: ConnectException) {
            MessageSendHelper.sendErrorMessage("failed to connect to api \n Check that you set the ip. \n if you have Message EBS#2574.")
            ex.printStackTrace()
        } catch (ex: IOException) {
            MessageSendHelper.sendChatMessage("Either Something went very wrong or WE FINSIHEDDD (x to doubt).")
            ex.printStackTrace()
        }
        disable()
        return null
    }

    // only valid during Break state
    private fun sendUpdate() {
        val assign = assignment!!
        val stats = breakState!!
        val mined = stats.blocksMined
        stats.blocksMined = 0
        val depth = stats.depth
        EXECUTOR.execute {
            try {
                println("Sending update on layer progress")
                doApiCall("update/${assign.layer}/${depth}/${username!!}/$mined", method = "POST")
            } catch (e: Exception) {
                // TODO: if the API is down we should probably disable?
                MessageSendHelper.sendChatMessage("Failed to send update to api")
            }
        }
    }

    private fun getAssignmentFromApi(posZ: Double): Assignment? {
        val parity = if (posZ > 0) "even" else  "odd"
        val apiResult = doApiCall("assign/$username/$parity", method = "PUT") ?: return null

        queue.clear()
        val lines = apiResult.lineSequence().iterator()
        val firstLine = lines.next()
        val split = firstLine.split(".");
        val layer = split[0].toInt()
        val isFail = split.size > 1 && split[1] == "failed"

        val data = mutableListOf<List<BlockPos>>()
        lines.forEach { line ->
            if (!line.isEmpty()) {
                val row = mutableListOf<BlockPos>()
                for (pair in line.split("#")) {
                    val numSplit = pair.split(" ")
                    val x = numSplit[0].toInt()
                    val z = numSplit[1].toInt()
                    row.add(BlockPos(x, 255, z))
                }
                data.add(row)
            }
        }
        queue.clear()
        // lol
        repeat((if (isFail) 2 else 1)) {
            for (i in 0 until data.size) {
                queue.add(Pair(LinkedHashSet(data[i]), i))
            }
        }
        queueSizeDesired = data.size

        val first = data.getOrNull(0)?.get(0)
        // TODO: these defaults are nonsense
        x = first?.x ?: 0
        z = first?.z ?: 0
        return Assignment(layer, isFail, data)
    }

    init {
        onEnable {
            state = State.ASSIGN
            busy = false
            empty = false

            username = mc.player.displayNameString
        }

        listener<ConnectionEvent.Disconnect> {
            disconnectHook()
        }

        listener<GuiEvent.Displayed> {
            (it.screen as? GuiDisconnected)?.let {
                disconnectHook()
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            val mc = Minecraft.getMinecraft()
            if (mc.player.dimension == 1 && x != 0 && z != 0) {
                disconnectHook()
            } else if (mc.player.dimension == 1 && state != State.QUEUE) {
                state = State.QUEUE
            }
            when (state) {
                State.ASSIGN -> {
                    delay = 0
                    loadChunkCount = 15
                    assignment = null
                    username = mc.player.displayNameString
                    EXECUTOR.execute {
                        Thread.sleep(100)
                        getAssignmentFromApi(mc.player.posZ)
                    }
                    state = State.LOAD
                }

                State.LOAD -> {
                    if (assignment != null) {
                        failurePosition = 0
                        state = State.TRAVEL
                    }
                }

                State.TRAVEL -> {
                    if (queue.isEmpty()) {
                        state = State.ASSIGN
                        MessageSendHelper.sendChatMessage("Task Queue is empty, requesting more assignments")
                    }

                    if (!BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive && queue.isNotEmpty()) {
                        selections.clear()
                        firstBlock = true
                        if (assignment!!.isFail) {
                            val layer = assignment!!.layer
                             if (failedLayerCounter == 0) {
                                 selections.clear()
                                 val temp = queue.last().first
                                 var tempZ = 0
                                 var tempX = 0
                                 for (coord in temp) {
                                     tempZ = coord.z
                                     tempX = coord.x
                                 }
                                 firstLineOfFailedFile = BlockPos(tempX, 255, tempZ)
                                 BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto ${2 + (xOffset + layer * 5)} 256 ${tempZ + zOffset + negPosCheck(layer)}")
                                 failedLayerCounter++
                             } else if (failedLayerCounter == 1) {
                                 val coord = queue.last().first
                                 var tempZ = 0
                                 for (i in coord) {
                                     tempZ = i.z
                                 }
                                 BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto ${2 + (xOffset + layer * 5)} 256 ${tempZ + zOffset + negPosCheck(layer)}")
                                 failedLayerCounter++
                             } else if (failedLayerCounter == 2) {
                                 val coord = queue.pollLast().first
                                 var completedBlocks = 0
                                 var totalBlocks = 0
                                 for (i in coord) {
                                     totalBlocks++
                                     if (mc.world.getBlockState(BlockPos(i.x, 255, i.z)).block !is BlockObsidian) {
                                         completedBlocks++
                                     }
                                 }
                                 if (completedBlocks == totalBlocks || queue.size == queueSizeDesired) {
                                     while(queue.size != queueSizeDesired) {
                                         queue.pollLast()
                                     }
                                     var positionInQueue = 0
                                     val positionDifference = queue.size - failurePosition
                                     while(positionInQueue <= positionDifference) {
                                         queue.poll()
                                         positionInQueue++
                                     }
                                     failedLayerCounter = 0
                                     failurePosition = 0

                                     backupCounter = 20
                                     state = State.BREAK
                                 } else {
                                     failedLayerCounter = 1
                                     failurePosition++
                                 }
                             }
                        } else {
                            backupCounter = 20
                            state = State.BREAK
                        }
                    }
                }

                State.BREAK -> {
                    if (!BaritoneAPI.getProvider().primaryBaritone.builderProcess.isActive && !BaritoneAPI.getProvider().primaryBaritone.mineProcess.isActive && !BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive) {
                        val layer = assignment!!.layer
                        val stats = breakState!!
                        if (breakCounter == 0) {
                            stats.blocksMined += brokenBlocksBuf
                            brokenBlocksBuf = 0
                            if (queue.isEmpty()) {
                                doApiCall("/finish/$username", method = "POST")
                                state = State.TRAVEL
                                return@safeListener
                            }
                            val tuple = queue.poll()
                            threeCoord = tuple.first
                            if (firstBlock) {
                                selections.add(threeCoord)
                                selections.add(threeCoord)
                            } else {
                                selections[1] = selections[0]
                                selections[0] = threeCoord
                            }
                            stats.depth = tuple.second
                            BaritoneAPI.getProvider().primaryBaritone.selectionManager.removeAllSelections()
                            var needToMine = false
                            val lastZ = z
                            for (coord in selections[1]) {
                                sel = BetterBlockPos(coord.x + xOffset, 255, coord.z + zOffset)
                                BaritoneAPI.getProvider().primaryBaritone.selectionManager.addSelection(sel, sel)
                                z = coord.z
                                x = coord.x
                                if (mc.world.getBlockState(BlockPos(x + xOffset,255,z + zOffset)).block is BlockObsidian) {
                                    needToMine = true
                                }
                            }
                            for (coord in selections[0]) {
                                sel = BetterBlockPos(coord.x + xOffset, 255, coord.z + zOffset)
                                BaritoneAPI.getProvider().primaryBaritone.selectionManager.addSelection(sel, sel)
                                z = coord.z
                                x = coord.x
                                if (mc.world.getBlockState(BlockPos(x + xOffset,255,z + zOffset)).block is BlockObsidian) {
                                    needToMine = true
                                    brokenBlocksBuf++
                                }
                            }
                            breakCounter++
                            if (needToMine || firstBlock || kotlin.math.abs(lastZ - z) > 1) {
                                if (mc.world.getBlockState(BlockPos(2 + (xOffset + layer * 5), 255 ,z + zOffset + negPosCheck(layer))) !is BlockAir || firstBlock || kotlin.math.abs(lastZ - z) > 1) { // thanks leijurv papi
                                    BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto ${2 + (xOffset + layer * 5)} 256 ${z + zOffset + negPosCheck(layer)}")
                                    firstBlock = false
                                }
                            }
                        } else if (breakCounter == 1) {
                            BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("sel set air")
                            breakCounter++
                        } else if (breakCounter == 2 && delay != 22) { // breakCounter 2 and else are for checking ghost blocks
                            delay++
                        } else {
                            delay = 0
                            breakCounter = 0
                            BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("sel set air")
                            if (backupCounter >= 20) {
                                sendUpdate()
                                backupCounter = 0
                            } else {
                                backupCounter++
                            }
                        }
                    }
                }
                State.QUEUE -> {
                // await joining server
                    val server = Minecraft.getMinecraft().currentServerData
                    if (server != null) {
                        if (mc.player.dimension == 0 && delayReconnect != 100 && server.serverIP.contains("2b2t")) {
                            delayReconnect++
                        } else if (mc.player.dimension == 0 && delayReconnect == 100 && server.serverIP.contains("2b2t")) {
                            state = State.ASSIGN
                            delayReconnect = 0
                        }
                        if (!server.serverIP.contains("2b2t")) {
                            runShutdownOnDisable = false
                            disable()
                        }
                    }
                }
            }
            if (player.posY < 200 && (state == State.BREAK || state == State.TRAVEL) && mc.player.dimension == 0) { // if player falls
                try {
                    println("Running bepatone shutdown hook")
                    disable()
                } catch (e: Exception) {
                    println("Running bepatone shutdown hook failed")
                }
            }
        }
        onDisable {
            state = State.ASSIGN
            BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("stop")
            runShutdownOnDisable = true
            sendUpdate()
        }
    }
    enum class State {
        ASSIGN, TRAVEL, BREAK, QUEUE, LOAD
    }

    private fun negPosCheck(fileNum: Int): Int {
        if (fileNum % 2 == 0) {
            return 1
        }
        return -1
    }
    private fun disconnectHook() {
        if (breakState != null) {
            sendUpdate()
        }
    }
}
