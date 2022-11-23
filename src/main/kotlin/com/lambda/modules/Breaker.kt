package com.lambda.modules

import baritone.api.BaritoneAPI
import baritone.api.utils.BetterBlockPos
import com.lambda.ExamplePlugin
import com.lambda.client.event.events.BlockBreakEvent
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.module.Category
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.util.Wrapper.world
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.init.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
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
    var queue: Queue<LinkedHashSet<BlockPos>> = LinkedList()

    var blocks_broken = 0

    var delay = 0

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

    private val url by setting("Server IP", "3.22.30.40")
    private val port by setting("Server Port", "19439")

    var id = "0";

    var state: State = State.ASSIGN

    private var threeCoord: LinkedHashSet<BlockPos> = LinkedHashSet();


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
                            BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto $tempX 256 $tempZ")
                        }
                    }
                }

                State.BREAK -> {
                    if (!BaritoneAPI.getProvider().primaryBaritone.builderProcess.isActive && !BaritoneAPI.getProvider().primaryBaritone.mineProcess.isActive && !BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive) {
                        if (breakCounter == 0) {
                            if (queue.isEmpty()) {
                                state = State.TRAVEL
                                return@safeListener
                            }
                            threeCoord = queue.poll()
                            BaritoneAPI.getProvider().primaryBaritone.selectionManager.removeAllSelections()
                            for (coord in threeCoord) {
//                                if (mc.world.getBlockState(coord.down()).block == Blocks.AIR) {
//                                    break
//                                }

//                                try {
//                                    println("Running bepatone shutdown hook")
//                                    val url = URL("https://ad3c-2600-3c02-00-f03c-93ff-fe0c-c02d.ngrok.io?x=${coord.x}&z=${coord.z}")
//
//                                    with(url.openConnection() as HttpURLConnection) {
//                                        requestMethod = "GET"  // optional default is GET
//
//                                        println("\nSent 'GET' request to URL : $url:$port; Response Code : $responseCode")
//
//                                    }
//                                    exitCoord = -20
//                                } catch (e: Exception) {
//                                    println("Running bepatone shutdown hook failed")
//                                }
                                val sel = BetterBlockPos(coord.x + xOffset, 255, coord.z + zOffset)
                                BaritoneAPI.getProvider().primaryBaritone.selectionManager.addSelection(sel, sel)
                                z = coord.z
                                x = coord.x
                            }
                            BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto ${2 + (xOffset + file * 5)} 256 ${z + zOffset + negPosCheck(file)}")

                            breakCounter++
                        } else if (breakCounter == 1) {
                            BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("sel set air")
                            breakCounter++
                        } else if (breakCounter == 2 && delay != 10) {
                            delay += 1
                        } else {
                            delay = 0
                            breakCounter = 0
                        }
                    }
                }
            }
            if (player.posY < 200) { // if player falls
                disable() // should run onDisable{}
            }
        }
        safeListener<ConnectionEvent.Disconnect> {
            try {
                println("Running bepatone shutdown hook")
                exitCoord = 0
                disable()
            } catch (e: Exception) {
                println("Running bepatone shutdown hook failed")

            }
        }

        onDisable {
            try {
                println("Running bepatone shutdown hook")

                println(mc.player.posY.toInt().toString())
                println(exitCoord)

                val url = URL("http://$url:$port/fail/${Breaker.file}/${Breaker.x + xOffset}/${mc.player.posY.toInt() + exitCoord}/${Breaker.z + zOffset}/${username}")

                with(url.openConnection() as HttpURLConnection) {
                    requestMethod = "GET"  // optional default is GET

                    println("\nSent 'GET' request to URL : $url:$port; Response Code : $responseCode")

                }
                exitCoord = -20
            } catch (e: Exception) {
                println("Running bepatone shutdown hook failed")
            }
        }
    }
}

enum class State() {
    ASSIGN, TRAVEL, BREAK
}

fun negPosCheck(fileNum: Int): Int {
    if (fileNum % 2 == 0) {
        return 1
    }
    return -1
}