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
import java.lang.Integer.parseInt
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Deque
import java.util.LinkedList
import java.util.Queue
import kotlin.collections.LinkedHashSet
import kotlin.concurrent.thread


internal object Breaker : PluginModule(
        name = "BepitoneBreaker",
        category = Category.MISC,
        description = "",
        pluginMain = ExamplePlugin
    ) {
    private var queue: Deque<LinkedHashSet<BlockPos>> = LinkedList() // in the future this should be a double ended queue which instead of snaking with files just snakes in the client
    var blocks_broken = 0
    private var brokenBlocksBuf = 0
    private var failedLayerCounter = 0
    private var delay = 0
    private var firstLineOfFailedFile : BlockPos = BlockPos(0,0,0)
    private var loadChunkCount = 15
    private var backupCounter = 20
    private var delayReconnect = 0
    private var breakCounter = 0
    var x = 0
    var z = 0
    var file = 0
    private var fileNameFull = ""
    private var busy = false
    private var empty = false
    private var queueSizeDesired = 0
    private var failurePosition = 0
    const val xOffset = -5000
    const val zOffset = -5000
    var username = ""
    private val url by setting("Server IP", "alightintheendlessvoid.org")
    private var id = "0"
    private var runShutdownOnDisable = true
    private var sel = BetterBlockPos(0,0,0)
    var state: State = State.ASSIGN
    private var firstBlock  = true
    private var threeCoord: LinkedHashSet<BlockPos> = LinkedHashSet()
    private var selections: ArrayList<LinkedHashSet<BlockPos>> = ArrayList(2)
    private var finishedWithFile = false
    private var restart = 1

    private fun backupCurrentFile() {
        val fileCopy = file
        val xCopy = x
        val zCopy = z
        val usernameCopy = username
        thread {
            try {
                println("Running bepitone shutdown hook") // kys lion

                val url = URL("http://$url/fail/${fileCopy}/${xCopy}/256/${zCopy}/${usernameCopy}")

                with(url.openConnection() as HttpURLConnection) {
                    requestMethod = "GET"  // optional default is GET
                    println("\nSent 'GET' request to URL : $url; Response Code : $responseCode")
                }
                MessageSendHelper.sendChatMessage("Layer successfully backed up!")
            } catch (e: Exception) {
                MessageSendHelper.sendChatMessage("Running backup failed, this is NOT catastrophic")
                println("Running bepitone shutdown hook failed")
            }
        }
    }

    private fun updateLeaderboard() {
        val blocksBrokenCopy = blocks_broken
        val usernameCopy = username
        blocks_broken = 0
        thread {
            try {
                val url = URL("http://$url/leaderboard/${usernameCopy}/${blocksBrokenCopy}")

                with(url.openConnection() as HttpURLConnection) {
                    requestMethod = "GET"  // optional default is GET
                    println("\nSent 'GET' request to URL : $url; Response Code : $responseCode")
                }
            } catch (e: Exception) {
                println("Running bepitone update leaderboard hook failed")
            }
        }
    }

    private fun readFile() {
        try {
            val url = URL("http://$url/assign/$file/$username/$restart")
            restart = 1
            var queueBuffer : Queue<LinkedHashSet<BlockPos>> = LinkedList()
            val connection = url.openConnection()
            var tempX:Int
            var tempZ:Int
            var firstX = 0
            var firstZ = 0
            var firstData = true
            var fileFirstLine = true
            queue.clear()
            BufferedReader(InputStreamReader(connection.getInputStream())).use { inp ->
                var line: String?
                while (inp.readLine().also { line = it } != null) {
                    if (fileFirstLine) {
                        fileFirstLine = false
                        fileNameFull = line.toString()
                        if (fileNameFull.contains(".")) {
                            file = fileNameFull.split(".")[0].toInt()
                        } else {
                            file = fileNameFull.toInt()
                        }
                    } else {
                        if (line.toString() == "") {
                            return@use
                        } else {
                            val threeTemp: LinkedHashSet<BlockPos> = LinkedHashSet()
                            for (coordSet in line.toString().split("#")) {
                                tempX = parseInt(coordSet.split(" ").get(0))
                                tempZ = parseInt(coordSet.split(" ").get(1))
                                threeTemp.add(BlockPos(tempX, 255, tempZ))
                                if (firstData) {
                                    firstData = false
                                    firstX = tempX
                                    firstZ = tempZ
                                }
                            }
                            if (fileNameFull.contains(".failed")) {
                                queueBuffer.add(threeTemp)
                            }
                            queue.add(threeTemp)
                        }
                    }
                }
                queueSizeDesired = queue.size
                if (fileNameFull.contains(".failed")) {
                    while (queueBuffer.isNotEmpty()) {
                        queue.add(queueBuffer.poll())
                    }
                }
                x = firstX // jfc never do this again
                z = firstZ
                finishedWithFile = true
            }
        } catch (_: ConnectException) {
            MessageSendHelper.sendErrorMessage("failed to connect to api \n Check that you set the ip. \n if you have Message EBS#2574.")
            disable()
        } catch (_: IOException) {
            MessageSendHelper.sendChatMessage("Either Something went very wrong or WE FINSIHEDDD (x to doubt).")
            disable()
        }
        finishedWithFile = true
    }

    init {
        onEnable {
            restart = 1
            state = State.ASSIGN
            busy = false
            empty = false

            try {
                val url = URL("http://$url/start")
                MessageSendHelper.sendChatMessage(url.toString())
                val connection = url.openConnection()
                BufferedReader(InputStreamReader(connection.getInputStream())).use { inp ->
                    id = inp.readLine()
                }
            } catch (_: ConnectException) {
                MessageSendHelper.sendErrorMessage("failed to connect to api \n Check that you set the ip.")
                disable()
            } catch (_: IOException) {
                MessageSendHelper.sendChatMessage("Either Something went very wrong or WE FINSIHEDDD (x to doubt).")
                disable()
            }
            if (mc.player.posZ > 0) {
                file = 0
            } else {
                file = 1
            }
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
                    finishedWithFile = false
                    username = mc.player.displayNameString
                    thread {
                        Thread.sleep(100)
                        readFile()
                    }
                    state = State.LOAD
                }

                State.LOAD -> {
                    if (finishedWithFile) {
                        failurePosition = 0
                        state = State.TRAVEL
                    }
                }

                State.TRAVEL -> {
                    if (queue.isEmpty()) {
                        state = State.ASSIGN
                        restart = 0
                        MessageSendHelper.sendChatMessage("Task Queue is empty, requesting more assignments")
                    }

                    if (!BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive && queue.isNotEmpty()) {
                        selections.clear()
                        firstBlock = true
                        if (fileNameFull.contains(".failed")) {
                             if (failedLayerCounter == 0) {
                                 selections.clear()
                                 val temp = queue.last()
                                 var tempZ = 0
                                 var tempX = 0
                                 for (coord in temp) {
                                     tempZ = coord.z
                                     tempX = coord.x
                                 }
                                 firstLineOfFailedFile = BlockPos(tempX, 255, tempZ)
                                 BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto ${2 + (xOffset + file * 5)} 256 ${tempZ + zOffset + negPosCheck(file)}")
                                 failedLayerCounter++
                             } else if (failedLayerCounter == 1) {
                                 val coord = queue.last()
                                 var tempZ = 0
                                 for (i in coord) {
                                     tempZ = i.z
                                 }
                                 BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto ${2 + (xOffset + file * 5)} 256 ${tempZ + zOffset + negPosCheck(file)}")
                                 failedLayerCounter++
                             } else if (failedLayerCounter == 2) {
                                 val coord = queue.pollLast()
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
                                     fileNameFull = fileNameFull.split(".")[0]
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
                        if (breakCounter == 0) {
                            blocks_broken+=brokenBlocksBuf
                            brokenBlocksBuf = 0
                            if (queue.isEmpty()) {
                                state = State.TRAVEL
                                return@safeListener
                            }
                            threeCoord = queue.poll()
                            if (firstBlock) {
                                selections.add(threeCoord)
                                selections.add(threeCoord)
                            } else {
                                selections[1] = selections[0]
                                selections[0] = threeCoord
                            }
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
                                if (mc.world.getBlockState(BlockPos(2 + (xOffset + file * 5), 255 ,z + zOffset + negPosCheck(file))) !is BlockAir || firstBlock || kotlin.math.abs(lastZ - z) > 1) { // thanks leijurv papi
                                    BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto ${2 + (xOffset + file * 5)} 256 ${z + zOffset + negPosCheck(file)}")
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
                                backupCurrentFile()
                                updateLeaderboard()
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
            if (runShutdownOnDisable) {
                if (fileNameFull.contains(".failed")) {
                    val fileCopy = file
                    thread {
                        Thread.sleep(1000)
                        try {
                            println("Running bepatone shutdown hook")

                            val url = URL("http://$url/fail/${fileCopy}/${firstLineOfFailedFile.x}/0/${firstLineOfFailedFile.z}/${username}")

                            with(url.openConnection() as HttpURLConnection) {
                                requestMethod = "GET"  // optional default is GET

                                println("\nSent 'GET' request to URL : $url; Response Code : $responseCode")

                            }
                        } catch (e: Exception) {
                            println("Running bepatone shutdown hook failed")
                        }
                    }
                } else {
                    val fileCopy = file
                    val xCopy = x
                    val zCopy = z
                    thread {
                        Thread.sleep(1000)
                        try {
                            println("Running bepatone shutdown hook")

                            val url = URL("http://$url/fail/${fileCopy}/${xCopy}/256/${zCopy}/${username}")

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
            runShutdownOnDisable = true
            val blocksBrokenCopy = blocks_broken
            blocks_broken = 0
            thread {
                Thread.sleep(1000)
                try {
                    val url = URL("http://$url/leaderboard/${username}/${blocksBrokenCopy}")

                    with(url.openConnection() as HttpURLConnection) {
                        requestMethod = "GET"  // optional default is GET
                        println("\nSent 'GET' request to URL : $url; Response Code : $responseCode")
                    }
                } catch (e: Exception) {
                    println("Running bepitone update leaderboard hook failed")
                }
            }
        }
    }
    enum class State() {
        ASSIGN, TRAVEL, BREAK, QUEUE, LOAD
    }

    private fun negPosCheck(fileNum: Int): Int {
        if (fileNum % 2 == 0) {
            return 1
        }
        return -1
    }
    private fun disconnectHook() {
        if (state != State.QUEUE) {
            if (fileNameFull.contains(".failed")) {
                delayReconnect = 0
                state = State.QUEUE
                val fileCopy = file
                val blocksBrokenCopy = blocks_broken
                blocks_broken = 0
                thread { // TODO FIX THIS
                    Thread.sleep(1000)
                    try {
                        println("Running bepitone shutdown hook") // kys lion

                        val url = URL("http://$url/fail/${fileCopy}/${firstLineOfFailedFile.x}/0/${firstLineOfFailedFile.z}/${username}")

                        with(url.openConnection() as HttpURLConnection) {
                            requestMethod = "GET"  // optional default is GET
                            println("\nSent 'GET' request to URL : $url; Response Code : $responseCode")
                        }
                    } catch (e: Exception) {
                        println("Running bepitone shutdown hook failed")
                    }
                    try {
                        val url = URL("http://$url/leaderboard/${username}/${blocksBrokenCopy}")

                        with(url.openConnection() as HttpURLConnection) {
                            requestMethod = "GET"  // optional default is GET
                            println("\nSent 'GET' request to URL : $url; Response Code : $responseCode")
                        }
                    } catch (e: Exception) {
                        println("Running bepatone update leaderboard hook failed")
                    }
                }
            } else {
                delayReconnect = 0
                state = State.QUEUE
                val fileCopy = file
                val xCopy = x
                val zCopy = z
                val blocksBrokenCopy = blocks_broken
                blocks_broken = 0
                thread {
                    Thread.sleep(1000)
                    try {
                        println("Running bepatone shutdown hook")

                        val url = URL("http://$url/fail/${fileCopy}/${xCopy}/256/${zCopy}/${username}")

                        with(url.openConnection() as HttpURLConnection) {
                            requestMethod = "GET"  // optional default is GET
                            println("\nSent 'GET' request to URL : $url; Response Code : $responseCode")
                        }
                    } catch (e: Exception) {
                        println("Running bepatone shutdown hook failed")
                    }
                    try {
                        val url = URL("http://$url/leaderboard/${username}/${blocksBrokenCopy}")

                        with(url.openConnection() as HttpURLConnection) {
                            requestMethod = "GET"  // optional default is GET
                            println("\nSent 'GET' request to URL : $url; Response Code : $responseCode")
                        }
                    } catch (e: Exception) {
                        println("Running bepatone update leaderboard hook failed")
                    }
                }
            }
        }
    }
}
