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
        // 640x400 at 2x on a 2400 screen: 560 a side.
        assertEquals(KeyboardSides(560, 560), KeyboardPlacement.sides(2400, 560, 1280))
        // Anchored left: the right half takes the rest.
        assertEquals(KeyboardSides(400, 720), KeyboardPlacement.sides(2400, 400, 1280))
    }

    @Test fun aSixthOfTheScreenIsTheNarrowestHalf() {
        // The RP6: 640x400 at 2x leaves 320 of 1920 a side, exactly a sixth.
        assertEquals(KeyboardSides(320, 320), KeyboardPlacement.sides(1920, 320, 1280))
        assertNull(KeyboardPlacement.sides(1920, 319, 1282))
        // Fit on the RP6 leaves 96 a side.
        assertNull(KeyboardPlacement.sides(1920, 96, 1728))
        // One side wide enough, the other not.
        assertNull(KeyboardPlacement.sides(1920, 100, 1280))
    }

    @Test fun aPictureFillingTheWidthLeavesNoHalves() {
        assertNull(KeyboardPlacement.sides(1920, 0, 1920))
        // Cropped past both edges.
        assertNull(KeyboardPlacement.sides(1920, -60, 2040))
    }
}
