package com.mrjackspade.kairo.frontend

import org.junit.Assert.*
import org.junit.Test

class KeyboardPlacementTest {
    @Test fun sixteenByTenAndWiderAreWide() {
        assertTrue(KeyboardPlacement.wide(1920, 1200))
        assertTrue(KeyboardPlacement.wide(1920, 1080))
        assertTrue(KeyboardPlacement.wide(2400, 1080))
        assertFalse(KeyboardPlacement.wide(1600, 1200))
        assertFalse(KeyboardPlacement.wide(1080, 1920))
        assertFalse(KeyboardPlacement.wide(0, 0))
    }

    @Test fun eachHalfIsTheFreeWidthBesideThePicture() {
        // 640x400 at 2x, centred on a 1920 screen.
        assertEquals(KeyboardSides(320, 320), KeyboardPlacement.sides(1920, 320, 1280))
        // Anchored left: the right half takes the rest.
        assertEquals(KeyboardSides(100, 540), KeyboardPlacement.sides(1920, 100, 1280))
    }

    @Test fun aPictureFillingTheWidthLeavesNoHalves() {
        assertNull(KeyboardPlacement.sides(1920, 0, 1920))
        // Cropped past both edges.
        assertNull(KeyboardPlacement.sides(1920, -60, 2040))
        // Anchored against one edge.
        assertNull(KeyboardPlacement.sides(1920, 0, 1280))
    }
}
