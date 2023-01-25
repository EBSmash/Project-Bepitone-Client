package com.lambda.modules

import baritone.api.BaritoneAPI
import baritone.api.pathing.goals.Goal
import baritone.api.pathing.goals.GoalXZ
import baritone.api.utils.BetterBlockPos
import com.lambda.ExamplePlugin
import com.lambda.RoofGoal
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.GuiEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.module.Category
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.util.Timer
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.block.BlockAir
import net.minecraft.block.BlockLiquid
import net.minecraft.block.BlockObsidian
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiDisconnected
import net.minecraft.init.Blocks
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.fluids.IFluidBlock
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.Integer.*
import java.lang.Math.abs
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.collections.LinkedHashSet


internal object Breaker : PluginModule(
        name = "BepitoneBreaker",
        category = Category.MISC,
        description = "",
        pluginMain = ExamplePlugin
    ) {
    private val EXECUTOR = Executors.newSingleThreadExecutor()
    private var brokenBlocksBuf = 0
    private var failedLayerTravelPhase = 0
    var failedLayerPosition = 0
    private var delayReconnect = 0
    var breakState: BreakState? = null
    var blocksMinedTotal = 0 // only used for the hud
    var prevAssignment: Assignment? = null
    var assignment: Assignment? = null
    const val X_OFFSET = -5000
    const val Z_OFFSET = -5000
    var username: String? = null
    private val url by setting("ServerIP", "bep.babbaj.dev")
    private val autoAssign by setting("AutoAssign", true)
    var state: State = State.ASSIGN


    //bepnet
    var beptimer = Timer();

    class BreakState(data: List<List<BlockPos>>, startIndex: Int) {
        var breakPhase = BreakPhase.SELECT
        var blocksMinedSinceLastUpdate = 0 // reset when sending update
        var depth: Int = startIndex
        // the int is the index in the layer data returned by the api
        var queue: Deque<Pair<LinkedHashSet<BlockPos>, Int>> = LinkedList()
        var selections: Array<LinkedHashSet<BlockPos>>? = null
        val packetAirBlocks: MutableSet<BlockPos> = ConcurrentHashMap.newKeySet() // blocks that an SPacketBlockChange says is air
        var waitDelay = 0
        var backupCounter = 5
        var firstBlock  = true

        init {
            for (i in startIndex until data.size) {
                queue.add(Pair(LinkedHashSet(data[i]), i))
            }
        }
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
            val constants = BreakPhase.values()
            return constants[(this.ordinal + 1) % constants.size]
        }
    }

    private fun doApiCall(path: String, method: String): String? {
        MessageSendHelper.sendChatMessage("/${path}")
        val url = URL("http://$url/$path")
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

        val lines = apiResult.lineSequence().iterator()
        val layer = lines.next().toInt()
        val isFail = lines.next().substring("failed=".length).toBooleanStrict()
        val baseDepth = lines.next().substring("depth=".length).toInt()

        val data = mutableListOf<List<BlockPos>>()
        lines.forEach { line ->
            if (line.isNotEmpty()) {
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
        assignment = Assignment(layer, baseDepth, isFail, data)
        prevAssignment = assignment
    }

    private fun isRowAir(world: World, row: List<BlockPos>): Boolean {
        return row.all { pos ->
            val block = world.getBlockState(BlockPos(pos.x + X_OFFSET, pos.y, pos.z + Z_OFFSET)).block
            return block == Blocks.AIR || block == Blocks.SNOW_LAYER
        }
    }
    private fun isRowOf5Air(world: World, layer: Int, z: Int): Boolean {
        val start = layer * 5 + X_OFFSET
        return (start..start + 4).all { x ->
            val block = world.getBlockState(BlockPos(x, 255, z)).block
            return block == Blocks.AIR || block == Blocks.SNOW_LAYER
        }
    }

    private fun startBreakPhase(startDepth: Int) {
        state = State.BREAK
        breakState = BreakState(assignment!!.data, startDepth)
    }

    private fun startTravelPhase() {
        state = State.TRAVEL
        failedLayerTravelPhase = 0
        failedLayerPosition = 0
    }

    private fun xOfLayer(layer: Int): Int {
        return layer * 5 + X_OFFSET
    }

    private fun negPosCheck(fileNum: Int): Int {
        if (fileNum % 2 == 0) {
            return 1
        }
        return -1
    }

    private fun nextZ(z: Int, layer: Int): Int {
        return if (layer % 2 == 0) z + 1 else z - 1
    }
    private fun prevZ(z: Int, layer: Int): Int {
        return if (layer % 2 == 0) z - 1 else z + 1
    }
    private fun finishBreak() {
        with(breakState!!) {
            // fix final stat being off by one
            depth = assignment!!.baseDepth + assignment!!.data.size
            sendUpdate()
            //EXECUTOR.execute { doApiCall("finish/${assignment!!.layer}", method = "PUT") }
            breakState = null
            assignment = null
            state = State.ASSIGN
        }
    }

    private fun goto(x: Int, z: Int) {
        BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.setGoalAndPath(RoofGoal(GoalXZ(x, z)))
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
            if (event.packet is SPacketBlockChange && breakState != null) {
                val packet = event.packet as SPacketBlockChange
                if (packet.getBlockState().getBlock() == Blocks.AIR) {
                    val pos = packet.getBlockPosition()
                    if (pos.y == 255) {
                        breakState!!.packetAirBlocks.add(pos)
                    }
                }
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            val mc = Minecraft.getMinecraft()
            if (player.posY < 200 && (state == State.BREAK || state == State.TRAVEL) && mc.player.dimension == 0) { // if player falls
                try {
                    MessageSendHelper.sendChatMessage("Disabling because below y 200")
                    disconnectHook()
                    disable()
                    return@safeListener
                } catch (e: Exception) {
                    println("Running bepatone shutdown hook failed")
                }
            }

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
                    if (!autoAssign) return@safeListener
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
                                mc.player.posZ < 0 // even rows start at low Z and mine towards high Z
                            }
                            getAssignmentFromApi(isEven)
                            if (assignment != null) with(assignment!!) {
                                MessageSendHelper.sendChatMessage("Got layer $layer, Depth = $baseDepth, ${data.size} rows, failed = $isFail")
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
                        startTravelPhase()
                    }
                }

                State.TRAVEL -> with(assignment!!) {
                    // TODO: this can probably be done earlier
                    if (data.isEmpty()) {
                        state = State.ASSIGN
                        doApiCall("finish/$layer", method = "PUT")
                        breakState = null
                        assignment = null
                        MessageSendHelper.sendChatMessage("Task Queue is empty, requesting more assignments")
                        return@safeListener
                    }

                    if (!BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive) {
                        if (isFail) {
                            val layer = layer
                            val middleX = 2 + xOfLayer(layer)
                            if (failedLayerTravelPhase == 0) {
                                // get the last row from the data, take the z from the first block
                                val z = data.last().first().z
                                // this is technically not necessary because the main fail travel algorithm will try to go to the same place but this ignores unloaded chunks
                                goto(middleX, z + Z_OFFSET + negPosCheck(layer))
                                failedLayerTravelPhase++
                            } else if (failedLayerTravelPhase == 1) {
                                var foundSolidRow = false
                                var foundAirRow = false
                                outer@ for (i in failedLayerPosition until data.size) {
                                    val row = data.asReversed()[i]
                                    val z = row.first().z + Z_OFFSET

                                    if (mc.world.isChunkGeneratedAt(middleX shr 4, z shr 4)) {
                                        if (!isRowAir(mc.world, row)) {
                                            foundSolidRow = true
                                        } else {
                                            foundAirRow = true
                                        }
                                    } else if (foundSolidRow || !foundAirRow) {
                                        // get closer to what we want to check so we can load more chunks
                                        failedLayerPosition = i
                                        goto(middleX, nextZ(z, layer))
                                        MessageSendHelper.sendChatMessage("goto $failedLayerPosition x = $middleX z = ${nextZ(z, layer)}")
                                        if (!foundAirRow) {
                                            MessageSendHelper.sendChatMessage("baritone pathing failed idiot")
                                        }
                                        return@safeListener
                                    } else if (foundAirRow) { // only break early if we have actually tried to look for and found air rows
                                        break
                                    } else { // no scanning happened, probably because goto didnt actually move us
                                        return@safeListener
                                    }
                                }

                                // TODO: if failedLayerPosition is 0 we can probably skip break phase
                                val startIndex = (data.size - 1) - failedLayerPosition
                                MessageSendHelper.sendChatMessage("Starting BreakPhase at index $startIndex")
                                startBreakPhase(startIndex)
                            }
                        } else {
                            startBreakPhase(0)
                        }
                    }
                }

                State.BREAK -> with(breakState!!) {
                    if (!BaritoneAPI.getProvider().primaryBaritone.builderProcess.isActive && !BaritoneAPI.getProvider().primaryBaritone.mineProcess.isActive && !BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive) {
                        //MessageSendHelper.sendChatMessage("queue = ${queue.size}")
                        val layer = assignment!!.layer
                        if (breakPhase == BreakPhase.SELECT) {
                            blocksMinedSinceLastUpdate += brokenBlocksBuf
                            blocksMinedTotal += brokenBlocksBuf
                            brokenBlocksBuf = 0
                            if (queue.isEmpty()) {
                                finishBreak()
                                return@safeListener
                            }
                            val tuple = queue.poll()
                            val row = tuple.first
                            val dataIndex = tuple.second
                            depth = dataIndex
                            selections = if (dataIndex == 0) {
                                arrayOf(row, row)
                            } else {
                                val prevRow = assignment!!.data[dataIndex - 1]
                                val prevZ = prevRow[0].z
                                if (abs(prevZ - row.first().z) <= 1) {
                                    arrayOf(row, LinkedHashSet(prevRow))
                                } else {
                                    arrayOf(row, row)
                                }
                            }
                            BaritoneAPI.getProvider().primaryBaritone.selectionManager.removeAllSelections()
                            var needToMine = false
                            for (coord in selections!![1]) {
                                val sel = BetterBlockPos(coord.x + X_OFFSET, 255, coord.z + Z_OFFSET)
                                if (mc.world.getBlockState(sel).block !is BlockLiquid &&
                                    mc.world.getBlockState(sel).block !is BlockAir &&
                                    mc.world.getBlockState(sel).block !is IFluidBlock) { // pretty much just "not water"
                                    needToMine = true
                                    BaritoneAPI.getProvider().primaryBaritone.selectionManager.addSelection(sel, sel)
                                }
                            }
                            for (coord in selections!![0]) {
                                val sel = BetterBlockPos(coord.x + X_OFFSET, 255, coord.z + Z_OFFSET)
                                if (mc.world.getBlockState(sel).block !is BlockLiquid &&
                                    mc.world.getBlockState(sel).block !is BlockAir &&
                                    mc.world.getBlockState(sel).block !is IFluidBlock) {
                                    needToMine = true
                                    BaritoneAPI.getProvider().primaryBaritone.selectionManager.addSelection(sel, sel)
                                    brokenBlocksBuf++
                                }
                            }
                            val currentZ = row.first().z
                            val currentMiddleX = 2 + layer * 5
                            val lastZ = assignment!!.data[max(dataIndex - 1, 0)].first().z
                            if (needToMine || firstBlock || kotlin.math.abs(lastZ - currentZ) > 1) {
                                // if we can stand on the block we want to goto or we are are in a state where we can't assume we are close to the layer
                                if (mc.world.getBlockState(BlockPos(X_OFFSET + currentMiddleX, 255 ,currentZ + Z_OFFSET + negPosCheck(layer))) !is BlockAir || firstBlock || kotlin.math.abs(lastZ - currentZ) > 1) { // thanks leijurv papi
                                    firstBlock = false
                                    // if the chunk is loaded we can trust that it really is air in the selection so we can skip it
                                    val isRowLoaded = mc.world.isChunkGeneratedAt((currentMiddleX + X_OFFSET) shr 4, (currentZ + Z_OFFSET) shr 4)
                                    if (isRowLoaded && !needToMine) {
                                        return@safeListener
                                    }
                                    // if the row is in unloaded chunks and there isn't a gap between the previous, then the rest of the layer from what we can see is all air, and we will assume that the rest of the entire layer is air
                                    /*if (!isRowLoaded && kotlin.math.abs(lastZ - currentZ) == 1) {
                                        finishBreak()
                                        return@safeListener
                                    }*/
                                    goto(2 + (X_OFFSET + layer * 5), currentZ + Z_OFFSET + negPosCheck(layer))
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
                        if (!server.serverIP.contains("2b2t") && !server.serverIP.contains("proxy")) {
                            disable()
                            return@safeListener
                        }
                        if (mc.player.dimension == 0 && delayReconnect != 100) {
                            delayReconnect++
                        } else if (mc.player.dimension == 0 && delayReconnect == 100) {
                            state = State.ASSIGN
                            delayReconnect = 0
                        }
                    }
                }
            }
        }
        onDisable {
            BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("stop")
            disconnectHook()
            state = State.ASSIGN
        }


        /*
        safeListener<ClientChatReceivedEvent> {
            if (it.message.formattedText.lowercase(Locale.getDefault()).contains("bep")){
                if (beptimer.time < 60000) return@safeListener
                player.sendChatMessage("bep")
                beptimer.reset()
            }
        }
         */

    }
    private fun disconnectHook() {
        state = State.QUEUE
        if (breakState != null) {
            sendUpdate()
        }
    }
}
