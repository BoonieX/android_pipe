package com.booniex.pipes

import com.booniex.pipes.data.BundleSide
import com.booniex.pipes.data.SideScan
import com.booniex.pipes.data.aggregateMax
import org.junit.Assert.assertEquals
import org.junit.Test

class AggregateTest {
    @Test
    fun maxOfSides() {
        val sides = listOf(
            SideScan(BundleSide.LEFT, "", 50, emptyList()),
            SideScan(BundleSide.RIGHT, "", 52, emptyList()),
            SideScan(BundleSide.FRONT, "", 48, emptyList()),
        )
        assertEquals(52, aggregateMax(sides))
    }

    @Test
    fun emptyIsZero() {
        assertEquals(0, aggregateMax(emptyList()))
    }
}
