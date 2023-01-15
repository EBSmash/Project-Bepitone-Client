package com.lambda.modules

import baritone.api.BaritoneAPI
import baritone.api.selection.ISelection
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
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Deque
import java.util.LinkedList
import kotlin.collections.LinkedHashSet
import kotlin.concurrent.thread

internal object Scanner : PluginModule(
    name = "Bepitone Scanner",
    category = Category.MISC,
    description = "X3 owo",
    pluginMain = ExamplePlugin
) {

    private var queue : Deque<LinkedHashSet<BlockPos>> = LinkedList()
    private val url = "localhost:8000"
    private val urlMain = "bep.babbaj.dev"
    private var currentFile = 0
    private var finishedWithFile = false
    private var state : CheckState = CheckState.REQUEST
    private var file = 0
    private var direction = 0

    private fun finish() {
        // fix final stat being off by one
        thread { doApiCall("finish/${file}", method = "PUT") }
    }
    private fun doApiCall(path: String, method: String): String? {
        MessageSendHelper.sendChatMessage("/${path}")
        val url = URL("http://$urlMain/$path")
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
    private fun logFailed (fileNum : Int) {
        val outputFile = File("${mc.gameDir}/failedscanner/failed.bep")
        outputFile.createNewFile()
        outputFile.appendText("$fileNum\n")
    }
    private fun requestNewFile() {
        thread {
            try {
                val url = URL("http://${url}/request/${direction}")
                queue.clear()
                val connection = url.openConnection()
                BufferedReader(InputStreamReader(connection.getInputStream())).use { inp ->
                    var line: String?
                    var fileFirstLine = true
                    while (inp.readLine().also { line = it } != null) {
                        if (fileFirstLine) {
                            fileFirstLine = false
                            if (line.toString().contains("DISABLE")) {
                                MessageSendHelper.sendChatMessage("Disabled by API")
                                disable()
                            } else {
                                file = line.toString().toInt()
                            }
                        } else {
                            if(line.toString() == "") {
                                return@use
                            } else {
                                val queueBuffer: LinkedHashSet<BlockPos> = LinkedHashSet()
                                 for (coordBuffer in line.toString().split("#")) {
                                     queueBuffer.add(BlockPos(coordBuffer.split(" ")[0].toInt(),
                                         255,
                                         coordBuffer.split(" ")[1].toInt()))
                                 }
                                 queue.add(queueBuffer)
                            }
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
            state = CheckState.REQUEST
            if (mc.player.posZ > 0) {
                direction = 0
            } else {
                direction = 1
            }
            currentFile = negPosCheck(mc.player.posZ.toInt())
            if (Breaker.isEnabled) {
                MessageSendHelper.sendChatMessage("Please disable Bepitone Breaker before using scanner.")
                disable()
            }
            val mainDirectory = File("${mc.gameDir}/failedscanner/")
            try {
                mainDirectory.mkdir()
                print("Creating new keekerclient config folder for keeklogger")
            } catch (e: FileAlreadyExistsException) {}
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
                            MessageSendHelper.sendChatMessage("Found failed layer")
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

    private enum class CheckState {
        TRAVEL, CHECK, REQUEST, LOAD
    }
}
