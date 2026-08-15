package com.modscreating.unlimitedspace.worldgen.space;

import com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyCoordinate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockPosToGalaxyCoordinateTest {

    @Test
    void positiveCoordinatesMap() {
        GalaxyCoordinate c = com.modscreating.unlimitedspace.worldgen.space.adapter.BlockPosToGalaxyCoordinate.fromBlock(512, 256);
        assertEquals(2.0, c.x(), 1e-9);
        assertEquals(1.0, c.z(), 1e-9);
    }

    @Test
    void negativeCoordinatesMap() {
        GalaxyCoordinate c = com.modscreating.unlimitedspace.worldgen.space.adapter.BlockPosToGalaxyCoordinate.fromBlock(-256, -1);
        assertEquals(-1.0, c.x(), 1e-9);
        assertEquals(-0.00390625, c.z(), 1e-9);
    }

    @Test
    void boundaryCoordinatesAreStable() {
        GalaxyCoordinate a = com.modscreating.unlimitedspace.worldgen.space.adapter.BlockPosToGalaxyCoordinate.fromBlock(255, 255);
        GalaxyCoordinate b = com.modscreating.unlimitedspace.worldgen.space.adapter.BlockPosToGalaxyCoordinate.fromBlock(256, 256);
        assertNotEquals(a.x(), b.x(), 1e-9);
        assertNotEquals(a.z(), b.z(), 1e-9);
    }
}