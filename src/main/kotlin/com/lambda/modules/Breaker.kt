package com.lambda.modules

import baritone.api.BaritoneAPI
import com.lambda.ExamplePlugin
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.Category
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.math.VectorUtils.toVec3dCenter
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.init.Blocks
import net.minecraft.network.play.client.CPacketAnimation
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.Integer.parseInt
import java.lang.Math.ceil
import java.net.ConnectException
import java.net.URL
import java.util.*
import javax.xml.ws.http.HTTPException


internal object Breaker : PluginModule(name = "BepitoneBreaker", category = Category.MISC, description = "", pluginMain = ExamplePlugin) {
    var queue: Queue<String> = PriorityQueue<String>()
    var list: ArrayList<String> = ArrayList()

    private val miningTimer = TickTimer(TimeUnit.TICKS)

    private var lastHitVec: Vec3d? = null

    var x = 0
    var z = 0

    var traveling = false

    var shouldBreak = false

    var empty = false;

    var state = 0

    var broken = true

    init {

        onEnable {
            state = 0;
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (state == 0) {
                //sent get req
                try {
                    val url = URL("http://127.0.0.1:8000/assign")
                    val connection = url.openConnection()
                    BufferedReader(InputStreamReader(connection.getInputStream())).use { inp ->
                        var line: String?
                        //for each line
                        while (inp.readLine().also { line = it } != null) {
                            //debug print
//                        MessageSendHelper.sendChatMessage(line.toString())

                            if (line.toString() == "") {
                                empty = true;
                                break
                            } else {
                                empty = false;
                            }

                            if (!empty) {
                                queue.add(line.toString())
                            }
                        }
                        if (empty) {
                            state = 0
                            return@safeListener
                        } else {
                            state = 1
                        }
                    }
                }
                catch (_: ConnectException){
                  MessageSendHelper.sendErrorMessage("failed to connect to api \n Message EBS#2574.")
                  disable()
                }
                catch (_: IOException){
                    MessageSendHelper.sendChatMessage("Either Something went very wrong or WE FINSIHEDDD.")
                    disable()
                }
            }
            if (state == 1) {
                if (!queue.isEmpty()) {
                    if (broken) {
                        var coord = queue.poll();

                        if (coord == null) {
                            state = 0
                            return@safeListener
                        }

                        x = parseInt(coord.split(" ")[0])
                        z = parseInt(coord.split(" ")[1])

                        broken = false;
                    }
                    if(mc.world.getBlockState(BlockPos(x, 255, z).south()).block == Blocks.AIR){
                        shouldBreak = false;
                        traveling = true;
                        broken = true
                    }
                    else{
                        shouldBreak = true;
                    }

                    //goto thingy
                    if (!traveling) {
                        BaritoneAPI.getProvider().primaryBaritone.commandManager.execute("goto $x 256 ${z + 1}")
                        traveling = true
                    }

                    if (!BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.isActive && shouldBreak) {
                        mineBlock(BlockPos(x, 255, z), true)
                        broken = true;
                        traveling = false
                    }

                    if (mc.player.world.getBlockState(BlockPos(x, 255, z)).block != Blocks.AIR){
                        mineBlock(BlockPos(x, 255, z), false)
                    }

                } else {
                    state = 0
                }
            }
        }
    }

    private fun SafeClientEvent.mineBlock(pos: BlockPos, pre: Boolean) {

        val center = pos.toVec3dCenter()
        val diff = player.getPositionEyes(1.0f).subtract(center)
        val normalizedVec = diff.normalize()
        val blockState = world.getBlockState(pos)

        val ticksNeeded = ceil(((1 / (blockState.getPlayerRelativeBlockHardness(player, world, pos))).toDouble())).toInt()
        val side = EnumFacing.getFacingFromVector(normalizedVec.x.toFloat(), normalizedVec.y.toFloat(), normalizedVec.z.toFloat())

        lastHitVec = center

        if (pre) {
            connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, pos, side))
        }

        if (miningTimer.tick(ticksNeeded, true)) {
            connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, side))
        }

        connection.sendPacket(CPacketAnimation(EnumHand.MAIN_HAND))
        player.swingArm(EnumHand.MAIN_HAND)
    }
}

