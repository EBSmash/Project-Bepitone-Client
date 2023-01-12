package com.lambda

import baritone.api.pathing.goals.Goal
import baritone.api.pathing.goals.GoalXZ

class RoofGoal(goal: GoalXZ) : Goal {
    var goal: GoalXZ
    init {
        this.goal = goal
    }

    override fun isInGoal(x: Int, y: Int, z: Int): Boolean {
        return goal.isInGoal(x, y, z)
    }

    override fun heuristic(x: Int, y: Int, z: Int): Double {
        if (y < 256) {
            return Double.POSITIVE_INFINITY
        }
        return goal.heuristic(x, y, z)
    }
}