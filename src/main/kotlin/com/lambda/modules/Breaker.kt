package com.lambda.modules

import baritone.api.BaritoneAPI
import baritone.api.utils.BetterBlockPos
import com.lambda.ExamplePlugin
import com.lambda.client.event.events.BlockBreakEvent
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.module.Category
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.Wrapper.world
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.block.BlockAir
import net.minecraft.block.BlockObsidian
import net.minecraft.client.Minecraft
import net.minecraft.init.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.Integer.parseInt
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.util.LinkedList
import java.util.Queue
import kotlin.collections.LinkedHashSet


internal object Breaker : PluginModule(name = "BepitoneBreaker", category = Category.MISC, description = "", pluginMain = ExamplePlugin) {
    var queue: Queue<LinkedHashSet<BlockPos>> = LinkedList() // in the future this should be a double ended queue which instead of snaking with files just snakes in the client

    var blocks_broken = 0
    var brokenBlocksBuf = 0

    var delay = 0
    var delayReconnect = 0
    var breakCounter = 0
    var x = 0
    var z = 0
    var file = 0
    var fileNameFull = ""
    private var busy = false
    private var empty = false

    var exitCoord = -20

    var fileFirstLine = true

    val xOffset = -5000
    val zOffset = -5000

    var username = "";

    private val url by setting("Server IP", "2.tcp.ngrok.io")
    private val port by setting("Server Port", "10696")

    var id = "0";

    private var sel = BetterBlockPos(0,0,0);

    var state: State = State.ASSIGN
    var firstBlock  = true
    private var threeCoord: LinkedHashSet<BlockPos> = LinkedHashSet();
    private var selections: ArrayList<LinkedHashSet<BlockPos>> = ArrayList(2)


    init {

        onEnable {
            state = State.ASSIGN;
            busy = false
            empty = false

            try {
                val url = URL("http://$url:$port/start")
                MessageSendHelper.sendChatMessage(url.toString())
                val connection = url.openConnection()
                BufferedReader(InputStreamReader(connection.getInputStream())).use { inp ->
                    id = inp.readLine()
                }
            } catch (_: ConnectException) {
                MessageSendHelper.sendErrorMessage("failed to connect to api \n Check that you set the ip. \n if you have Message EBS#2574.")
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

        safeListener<TickEvent.ClientTickEvent> {

            // Disconnect
            val mc = Minecraft.getMinecraft()
            val data = mc.currentServerData
            if (data == null && state != State.QUEUE) { // should only run once
                println("DISCONNECT DETECTED")
                state = State.QUEUE
                try {
                    println("Running bepatone shutdown hook")
                    println(exitCoord)

                    val url = URL("http://$url:$port/fail/${Breaker.file}/${Breaker.x}/256/${Breaker.z}/${username}")

                    with(url.openConnection() as HttpURLConnection) {
                        requestMethod = "GET"  // optional default is GET
                        println("\nSent 'GET' request to URL : $url:$port; Response Code : $responseCode")
                    }
                } catch (e: Exception) {
                    println("Running bepatone shutdown hook failed")
                }
            }

            when (state) {

                State.ASSIGN -> {
                    try {
                        val url = URL("http://$url:$port/assign/$file/$username")
                        val connection = url.openConnection()
                        queue.clear()
                        BufferedReader(InputStreamReader(connection.getInputStream())).use { inp ->
                            var line: String?
                            //for each line
                            fileFirstLine = true
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
                                        var threeTemp: LinkedHashSet<BlockPos> = LinkedHashSet()
                                        for (coordSet in line.toString().split("#")) {
                                            x = parseInt(coordSet.split(" ").get(0))
                                            z = parseInt(coordSet.split(" ").get(1))
                                            threeTemp.add(BlockPos(x, 255, z))
                                        }
                                        queue.add(threeTemp)
                                    }
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

                    if (!BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive && queue.isNotEmpty()) {
                        MessageSendHelper.sendChatMessage("Traveling")
                        state = State.BREAK
                        if (fileNameFull.contains(".failed")) {
                            var temp = queue.last()
                            var tempX = 0
                            var tempZ = 0
                            for (coord in temp) {
                                tempX = coord.x
                                tempZ = coord.z
                            }
                            BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto ${tempX + xOffset} 256 ${tempZ + zOffset}")
                        }
                        selections.clear()
                        firstBlock = true
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
                                firstBlock = false
                                selections.add(threeCoord)
                                selections.add(threeCoord)
                            } else {
                                selections[1] = selections[0]
                                selections[0] = threeCoord
                            }
                            BaritoneAPI.getProvider().primaryBaritone.selectionManager.removeAllSelections()
                            var needToMine = false
                            for (coord in selections[1]) {
                                sel = BetterBlockPos(coord.x + xOffset, 255, coord.z + zOffset)
                                BaritoneAPI.getProvider().primaryBaritone.selectionManager.addSelection(sel, sel)
                                z = coord.z
                                x = coord.x
                                if (mc.world.getBlockState(BlockPos(x + xOffset,255,z + zOffset)).block !is BlockAir) {
                                    needToMine = true
                                }
                            }
                            for (coord in selections[0]) {
                                sel = BetterBlockPos(coord.x + xOffset, 255, coord.z + zOffset)
                                BaritoneAPI.getProvider().primaryBaritone.selectionManager.addSelection(sel, sel)
                                z = coord.z
                                x = coord.x
                                if (mc.world.getBlockState(BlockPos(x + xOffset,255,z + zOffset)).block !is BlockAir) {
                                    MessageSendHelper.sendChatMessage("bep")
                                    needToMine = true
                                    brokenBlocksBuf++
                                }
                            }
                            MessageSendHelper.sendChatMessage(needToMine.toString())
                            breakCounter++
                            if (needToMine) {
                                if (mc.world.getBlockState(BlockPos(2 + (xOffset + file * 5), 255 ,z + zOffset + negPosCheck(file))) !is BlockAir) { // thanks leijurv papi
                                    BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto ${2 + (xOffset + file * 5)} 256 ${z + zOffset + negPosCheck(file)}")
                                }
                            } else {
                                breakCounter = 0
                            }
                        } else if (breakCounter == 1) {
                            BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("sel set air")
                            breakCounter++
                        } else if (breakCounter == 2 && delay != 22) {
                            delay++
                        } else {
                            delay = 0
                            breakCounter = 0
                            BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("sel set air")
                        }
                    }
                }
                State.QUEUE -> {
                    // await joining server
                    if (data != null) {
                        if (mc.player.dimension == 0 && delayReconnect != 20) {
                            delayReconnect++
                        } else if (mc.player.dimension == 0 && delayReconnect == 20) {
                            state = State.ASSIGN
                            delayReconnect = 0
                        }
                    }
                }
            }

            if (player.posY < 200 && (state == State.BREAK || state == State.TRAVEL)) { // if player falls
                MessageSendHelper.sendChatMessage("dis you paper?")
                try {
                    println("Running bepatone shutdown hook")
                    disable() // does this run the disable shutdown hook or not? idk if it's been working correctly
                } catch (e: Exception) {
                    println("Running bepatone shutdown hook failed")
                }
            }
        }
        onDisable {
            try {
                println("Running bepatone shutdown hook")

                println(mc.player.posY.toInt().toString())
                BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("stop")

                val url = URL("http://$url:$port/fail/${Breaker.file}/${Breaker.x}/256/${Breaker.z}/${username}")

                with(url.openConnection() as HttpURLConnection) {
                    requestMethod = "GET"  // optional default is GET

                    println("\nSent 'GET' request to URL : $url:$port; Response Code : $responseCode")

                }
            } catch (e: Exception) {
                println("Running bepatone shutdown hook failed")
            }
        }
    }
}

enum class State() {
    ASSIGN, TRAVEL, BREAK, QUEUE
}

fun negPosCheck(fileNum: Int): Int {
    if (fileNum % 2 == 0) {
        return 1
    }
    return -1
}