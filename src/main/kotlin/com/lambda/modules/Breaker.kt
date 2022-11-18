package com.lambda.modules

import baritone.api.BaritoneAPI
import baritone.api.utils.BetterBlockPos
import com.lambda.ExamplePlugin
import com.lambda.client.module.Category
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.Integer.parseInt
import java.net.ConnectException
import java.net.URL
import java.util.LinkedList
import java.util.Queue
import kotlin.collections.LinkedHashSet


internal object Breaker : PluginModule(name = "BepitoneBreaker", category = Category.MISC, description = "", pluginMain = ExamplePlugin) {
    var queue: Queue<LinkedHashSet<BlockPos>> = LinkedList()

    var breakCounter = 0
    var x = 0
    var z = 0
    var file = 0
    var fileNameFull = ""
    private var busy = false
    private var empty = false;

    var fileFirstLine = true

    val xOffset = 0
    val zOffset = 0

    var id = "0";

    var state: State = State.ASSIGN

    private var threeCoord: LinkedHashSet<BlockPos> = LinkedHashSet();

    private val server by setting("Bepitone API Server IP", "Unchanged")

    init {

        onEnable {
            state = State.ASSIGN;
            busy = false
            empty = false
            if (mc.player != null) {
                BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("breakfromabove true")
                BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("blockreachdistance 2")
            }
            try {
                val url = URL("http://localhost:8000/start")
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
        }

        safeListener<TickEvent.ClientTickEvent> {
            when (state) {

                State.ASSIGN -> {
                    try {
                        val url = URL("http://localhost:8000/assign/$file")
                        val connection = url.openConnection()
                        queue.clear()
                        BufferedReader(InputStreamReader(connection.getInputStream())).use { inp ->
                            file++
                            var line: String?
                            //for each line
                            fileFirstLine = true
                            while (inp.readLine().also { line = it } != null) {
                                if (fileFirstLine) {
                                    fileFirstLine = false
                                    file = line.toString().split(".")[0].toInt()
                                    fileNameFull = line.toString()
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
                                val sel = BetterBlockPos(coord.x, 255, coord.z)
                                BaritoneAPI.getProvider().primaryBaritone.selectionManager.addSelection(sel, sel)
                                z = coord.z
                            }
                            BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto ${2 + (xOffset + file * 5)} 256 ${z + negPosCheck(file)}")
                            breakCounter++
                        } else if (breakCounter == 1) {
                            BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("sel set air")
                            breakCounter = 0
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

fun negPosCheck(fileNum: Int): Int {
    if (fileNum % 2 == 0) {
        return 1
    }
    return -1
}