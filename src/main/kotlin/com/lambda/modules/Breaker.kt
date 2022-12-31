package com.lambda.modules

import baritone.api.BaritoneAPI
import baritone.api.utils.BetterBlockPos
import com.lambda.ExamplePlugin
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.GuiEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.module.Category
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.block.BlockAir
import net.minecraft.block.BlockObsidian
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiDisconnected
import net.minecraft.init.Blocks
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.Integer.max
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Deque
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
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
    private var backupCounter = 20
    private var delayReconnect = 0
    var breakState: BreakState? = null
    var blocksMinedTotal = 0 // only used for the hud
    private var breakPhase = BreakPhase.SELECT
    private var waitDelay = 0
    var prevAssignment: Assignment? = null
    var assignment: Assignment? = null
    private var queueSizeDesired = 0
    private var failurePosition = 0
    const val X_OFFSET = -5000
    const val Z_OFFSET = -5000
    var username: String? = null
    //private val url by setting("Server IP", "bep.babbaj.dev")
    var state: State = State.ASSIGN
    private var firstBlock  = true
    private var selections: Array<LinkedHashSet<BlockPos>>? = null
    private val packetAirBlocks: MutableSet<BlockPos> = ConcurrentHashMap.newKeySet() // blocks that an SPacketBlockChange says is air

    class BreakState {
        var blocksMinedSinceLastUpdate = 0 // reset when sending update
        var depth = 0
    }

    class Assignment(val layer: Int, val baseDepth: Int, val isFail: Boolean, val data: List<List<BlockPos>>)

    enum class State {
        ASSIGN, TRAVEL, BREAK, QUEUE, LOAD
    }

    enum class BreakPhase {
        SELECT, // create the selections and then goto
        SET_AIR, // after goto, set air to start mining
        WAIT; // wait til server confirms blocks are broken or on the 1 second delay

        fun next(): BreakPhase {
            val constants = BreakPhase.values();
            return constants[(this.ordinal + 1) % constants.size]
        }
    }

    private fun doApiCall(path: String, method: String): String? {
        MessageSendHelper.sendChatMessage("/${path}")
        val realUrl = "bep.babbaj.dev";
        val url = URL("http://$realUrl/$path")
        val con = url.openConnection() as HttpURLConnection
        con.requestMethod = method
        con.setRequestProperty("bep-api-key", "48a24e8304a49471404bd036ed7e814bdd59d902d51a47a4bcb090e2fb284f70")
        try {
            val responseCode = con.getResponseCode()
            if (responseCode in 200..299) {
                return BufferedReader(InputStreamReader(con.getInputStream())).readText()
            }

            MessageSendHelper.sendChatMessage("Api call to $path returned an error ($responseCode):")
            val stream = con.getErrorStream()
            stream?.let {
                val text = BufferedReader(InputStreamReader(stream)).readText()
                MessageSendHelper.sendChatMessage(text)
                println(text)
            }

            return null
        } catch (ex: ConnectException) {
            MessageSendHelper.sendErrorMessage("failed to connect to api \n Check that you set the ip. \n if you have Message EBS#2574.")
            ex.printStackTrace()
        } catch (ex: Exception) {
            MessageSendHelper.sendChatMessage("Either Something went very wrong or WE FINSIHEDDD (x to doubt).")
            ex.printStackTrace()
        }
        disable()
        return null
    }

    // only valid during Break state HELL NAH!
    private fun sendUpdate() {
        val assign = assignment!!
        val stats = breakState!!
        val mined = stats.blocksMinedSinceLastUpdate
        stats.blocksMinedSinceLastUpdate = 0
        val depth = assign.baseDepth + stats.depth
        EXECUTOR.execute {
            try {
                println("Sending update on layer progress")
                doApiCall("update/${assign.layer}/$depth/${username!!}/$mined", method = "POST")
            } catch (e: Exception) {
                MessageSendHelper.sendChatMessage("Failed to send update to api (${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun getAssignmentFromApi(isEven: Boolean) {
        val parity = if (isEven) "even" else  "odd"
        val apiResult = doApiCall("assign/${username!!}/$parity", method = "PUT") ?: return

        queue.clear()
        val lines = apiResult.lineSequence().iterator()
        val layer = lines.next().toInt()
        val isFail = lines.next().substring("failed=".length).toBooleanStrict()
        val baseDepth = lines.next().substring("depth=".length).toInt()

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

        assignment = Assignment(layer, baseDepth, isFail, data)
        prevAssignment = assignment
        breakState = BreakState()
    }

    init {
        onEnable {
            state = State.ASSIGN
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

        listener<PacketEvent.PostReceive> { event ->
            if (event.packet is SPacketBlockChange) {
                val packet = event.packet as SPacketBlockChange
                if (packet.getBlockState().getBlock() == Blocks.AIR) {
                    val pos = packet.getBlockPosition()
                    if (pos.y == 255) {
                        packetAirBlocks.add(pos)
                    }
                }
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            val mc = Minecraft.getMinecraft()

            if (state != State.QUEUE && mc.player.dimension == 1) {
                disconnectHook()
                if (prevAssignment == null) {
                    MessageSendHelper.sendChatMessage("Disabling because in queue with no previous assignment")
                    disable()
                }
                state = State.QUEUE // onDisable sets state to assign
            }

            when (state) {
                State.ASSIGN -> {
                    waitDelay = 0
                    assignment = null
                    breakState = null
                    username = mc.player.displayNameString
                    EXECUTOR.execute {
                        Thread.sleep(100)
                        try {
                            MessageSendHelper.sendChatMessage("Requesting assignment from the API")
                            val isEven = if (prevAssignment != null) {
                                prevAssignment!!.layer % 2 == 1 // we want the opposite of the previous
                            } else {
                                mc.player.posZ < 0
                            }
                            getAssignmentFromApi(isEven)
                            if (assignment != null) {
                                MessageSendHelper.sendChatMessage("Got layer ${assignment!!.layer}, Depth = ${assignment!!.baseDepth}, ${assignment!!.data.size} rows, failed = ${assignment!!.isFail}")
                            }
                        } catch (ex: Exception) {
                            MessageSendHelper.sendChatMessage("getAssignmentFromApi threw an exception ($ex)")
                            ex.printStackTrace()
                        }
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
                        if (assignment != null) {
                            doApiCall("finish/${assignment!!.layer}", method = "PUT")
                            breakState = null
                            assignment = null
                        }
                        MessageSendHelper.sendChatMessage("Task Queue is empty, requesting more assignments")
                    }

                    if (!BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive && queue.isNotEmpty()) {
                        selections = null
                        firstBlock = true
                        if (assignment!!.isFail) {
                            val layer = assignment!!.layer
                             if (failedLayerCounter == 0) {
                                 val tempZ = queue.last().first.last().z
                                 BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto ${2 + (X_OFFSET + layer * 5)} 256 ${tempZ + Z_OFFSET + negPosCheck(layer)}")
                                 failedLayerCounter++
                             } else if (failedLayerCounter == 1) {
                                 val coord = queue.last().first
                                 var tempZ = 0
                                 for (i in coord) {
                                     tempZ = i.z
                                 }
                                 BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto ${2 + (X_OFFSET + layer * 5)} 256 ${tempZ + Z_OFFSET + negPosCheck(layer)}")
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
                                     breakPhase = BreakPhase.SELECT
                                     state = State.BREAK
                                 } else {
                                     failedLayerCounter = 1
                                     failurePosition++
                                 }
                             }
                        } else {
                            backupCounter = 20
                            breakPhase = BreakPhase.SELECT
                            state = State.BREAK
                        }
                    }
                }

                State.BREAK -> {
                    if (!BaritoneAPI.getProvider().primaryBaritone.builderProcess.isActive && !BaritoneAPI.getProvider().primaryBaritone.mineProcess.isActive && !BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive) {
                        val layer = assignment!!.layer
                        val stats = breakState!!
                        if (breakPhase == BreakPhase.SELECT) {
                            stats.blocksMinedSinceLastUpdate += brokenBlocksBuf
                            blocksMinedTotal += brokenBlocksBuf
                            brokenBlocksBuf = 0
                            if (queue.isEmpty()) {
                                sendUpdate()
                                doApiCall("finish/$layer", method = "PUT")
                                breakState = null
                                assignment = null
                                state = State.ASSIGN
                                return@safeListener
                            }
                            val tuple = queue.poll()
                            val threeCoord = tuple.first
                            if (selections == null) {
                                selections = arrayOf(threeCoord, threeCoord)
                            } else {
                                selections = arrayOf(threeCoord, selections!![0])
                            }
                            stats.depth = tuple.second
                            BaritoneAPI.getProvider().primaryBaritone.selectionManager.removeAllSelections()
                            var needToMine = false
                            for (coord in selections!![1]) {
                                val sel = BetterBlockPos(coord.x + X_OFFSET, 255, coord.z + Z_OFFSET)
                                BaritoneAPI.getProvider().primaryBaritone.selectionManager.addSelection(sel, sel)
                                val x = coord.x
                                val z = coord.z
                                if (mc.world.getBlockState(BlockPos(x + X_OFFSET,255,z + Z_OFFSET)).block is BlockObsidian) {
                                    needToMine = true
                                }
                            }
                            for (coord in selections!![0]) {
                                val sel = BetterBlockPos(coord.x + X_OFFSET, 255, coord.z + Z_OFFSET)
                                BaritoneAPI.getProvider().primaryBaritone.selectionManager.addSelection(sel, sel)
                                val x = coord.x
                                val z = coord.z
                                if (mc.world.getBlockState(BlockPos(x + X_OFFSET,255,z + Z_OFFSET)).block is BlockObsidian) {
                                    needToMine = true
                                    brokenBlocksBuf++
                                }
                            }
                            val currentZ = tuple.first.first().z
                            val currentMiddleX = 2 + layer * 5
                            val lastZ = assignment!!.data[max(tuple.second, 1) - 1].first().z
                            if (needToMine || firstBlock || kotlin.math.abs(lastZ - currentZ) > 1) {
                                // what the fuck is this line
                                if (mc.world.getBlockState(BlockPos(X_OFFSET + currentMiddleX, 255 ,currentZ + Z_OFFSET + negPosCheck(layer))) !is BlockAir || firstBlock || kotlin.math.abs(lastZ - currentZ) > 1) { // thanks leijurv papi
                                    firstBlock = false
                                    // if the chunk is loaded we can trust that it really is air in the selection so we can skip it
                                    if (mc.world.isChunkGeneratedAt((currentMiddleX + X_OFFSET) shr 4, (currentZ + Z_OFFSET) shr 4) && !needToMine) {
                                        return@safeListener
                                    }
                                    BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto ${2 + (X_OFFSET + layer * 5)} 256 ${currentZ + Z_OFFSET + negPosCheck(layer)}")
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
                            disable()
                        }
                    }
                }
            }
            if (player.posY < 200 && (state == State.BREAK || state == State.TRAVEL) && mc.player.dimension == 0) { // if player falls
                try {
                    MessageSendHelper.sendChatMessage("Disabling because below y 200")
                    disconnectHook()
                    disable()
                } catch (e: Exception) {
                    println("Running bepatone shutdown hook failed")
                }
            }
        }
        onDisable {
            BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("stop")
            disconnectHook()
            state = State.ASSIGN
        }
    }

    private fun negPosCheck(fileNum: Int): Int {
        if (fileNum % 2 == 0) {
            return 1
        }
        return -1
    }
    private fun disconnectHook() {
        state = State.QUEUE
        if (breakState != null) {
            sendUpdate()
        }
    }
}
