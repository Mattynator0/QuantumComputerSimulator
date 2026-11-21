package org.example.math;

import org.junit.jupiter.api.Test;

import static org.example.TestUtils.assertCloseTo;
import static org.junit.jupiter.api.Assertions.*;

public class MathUtilsTest {

    @Test
    void isBitSet() {
        assertTrue(MathUtils.isBitSet(0b0101, 0));
        assertFalse(MathUtils.isBitSet(0b0101, 1));
        assertTrue(MathUtils.isBitSet(0b0101, 2));
        assertFalse(MathUtils.isBitSet(0b0101, 3));
        assertFalse(MathUtils.isBitSet(0, 0));
        assertTrue(MathUtils.isBitSet(1 << 31, 31));
    }

    @Test
    void flipBinary() {
        assertEquals(10, MathUtils.flipBinary(5, 4));
        assertEquals(0b100, MathUtils.flipBinary(0b001, 3));
        assertEquals(0, MathUtils.flipBinary(0, 4));
        assertEquals(0b1111, MathUtils.flipBinary(0b1111, 4));
        assertEquals(5, MathUtils.flipBinary(MathUtils.flipBinary(5, 4), 4));
        assertEquals(0, MathUtils.flipBinary(7, 0));
    }

    @Test
    void arrayFromPredicate() {
        assertArrayEquals(new int[]{0, 2, 4, 6}, MathUtils.arrayFromPredicate(3, i -> i % 2 == 0));
        assertArrayEquals(new int[]{1, 3, 5, 7}, MathUtils.arrayFromPredicate(3, i -> (i & 1) == 1));
        assertArrayEquals(new int[]{}, MathUtils.arrayFromPredicate(3, i -> false));
        assertArrayEquals(new int[]{0}, MathUtils.arrayFromPredicate(0, i -> true));
        assertArrayEquals(new int[]{}, MathUtils.arrayFromPredicate(0, i -> false));
    }

    @Test
    void getMask() {
        assertEquals(0b1110, MathUtils.getMask(1, 4));
        assertEquals(0b0111, MathUtils.getMask(0, 3));
        assertEquals(0, MathUtils.getMask(0, 0));
        assertEquals(0, MathUtils.getMask(3, 1));
        assertEquals(1 << 5, MathUtils.getMask(5, 6));
    }

    @Test
    void twosComplementToNegative() {
        assertEquals(5, MathUtils.twosComplementToNegative(0b0101, 4));
        assertEquals(7, MathUtils.twosComplementToNegative(0b0111, 4));
        assertEquals(0, MathUtils.twosComplementToNegative(0, 4));
        assertEquals(-5, MathUtils.twosComplementToNegative(0b1011, 4));
        assertEquals(-8, MathUtils.twosComplementToNegative(0b1000, 4));
        assertEquals(-1, MathUtils.twosComplementToNegative(0b1111, 4));
        assertEquals(-1, MathUtils.twosComplementToNegative(1, 1));
    }

    @Test
    void getOptimalGroverIterations() {
        assertEquals(1, MathUtils.getOptimalGroverIterations(2, 1));
        assertEquals(2, MathUtils.getOptimalGroverIterations(3, 1));
        assertEquals((int) Math.floor(Math.PI / 4 * Math.sqrt((1 << 4) / 2.0)),
                MathUtils.getOptimalGroverIterations(4, 2));
        assertEquals(0, MathUtils.getOptimalGroverIterations(0, 1));
    }

    @Test
    void log2() {
        assertCloseTo(0, MathUtils.log2(1));
        assertCloseTo(3, MathUtils.log2(8));
        assertCloseTo(-1, MathUtils.log2(0.5));
        assertCloseTo(Math.log(10) / Math.log(2), MathUtils.log2(10));
    }

    @Test
    void ceilLog2() {
        assertEquals(0, MathUtils.ceilLog2(1));
        assertEquals(3, MathUtils.ceilLog2(8));
        assertEquals(4, MathUtils.ceilLog2(9));
        assertEquals(1, MathUtils.ceilLog2(2));
    }

    @Test
    void floorLog2() {
        assertEquals(0, MathUtils.floorLog2(1));
        assertEquals(3, MathUtils.floorLog2(8));
        assertEquals(3, MathUtils.floorLog2(9));
        assertEquals(1, MathUtils.floorLog2(2));
    }

    @Test
    void modPow() {
        assertEquals(24, MathUtils.modPow(2, 10, 1000));
        assertEquals(5, MathUtils.modPow(3, 5, 7));
        assertEquals(1, MathUtils.modPow(7, 0, 5));
        assertEquals(0, MathUtils.modPow(10, 3, 8));
    }

    @Test
    void gcd() {
        assertEquals(4, MathUtils.gcd(12, 8));
        assertEquals(1, MathUtils.gcd(17, 13));
        assertEquals(5, MathUtils.gcd(0, 5));
        assertEquals(5, MathUtils.gcd(5, 0));
        assertEquals(6, MathUtils.gcd(-18, 12));
        assertEquals(0, MathUtils.gcd(0, 0));
    }

    @Test
    void grayCode() {
        assertArrayEquals(new int[]{0}, MathUtils.grayCode(0));
        assertArrayEquals(new int[]{0, 1}, MathUtils.grayCode(1));
        assertArrayEquals(new int[]{0, 1, 3, 2}, MathUtils.grayCode(2));
        assertArrayEquals(new int[]{0, 1, 3, 2, 6, 7, 5, 4}, MathUtils.grayCode(3));

        int[] gray = MathUtils.grayCode(4);
        assertEquals(16, gray.length);
        for (int i = 0; i < gray.length; i++) {
            assertEquals(i ^ (i >> 1), gray[i]);
        }
        for (int i = 1; i < gray.length; i++) {
            assertEquals(1, Integer.bitCount(gray[i] ^ gray[i - 1]));
        }
    }

    @Test
    void toBinaryString() {
        assertEquals("0101", MathUtils.toBinaryString(5, 4));
        assertEquals("101", MathUtils.toBinaryString(5, 3));
        assertEquals("0000", MathUtils.toBinaryString(0, 4));
        assertEquals("", MathUtils.toBinaryString(7, 0));
        assertEquals("1", MathUtils.toBinaryString(1, 1));
        assertEquals("1111", MathUtils.toBinaryString(15, 4));
    }

    @Test
    void binaryToInteger() {
        assertEquals(5, MathUtils.binaryToInteger("0101"));
        assertEquals(10, MathUtils.binaryToInteger("1010"));
        assertEquals(0, MathUtils.binaryToInteger(""));
        assertEquals(0, MathUtils.binaryToInteger("0000"));
        assertEquals(1, MathUtils.binaryToInteger("1"));
        assertEquals(15, MathUtils.binaryToInteger("1111"));
    }

    @Test
    void toBinaryString_roundTrip() {
        for (int i = 0; i < 16; i++) {
            assertEquals(i, MathUtils.binaryToInteger(MathUtils.toBinaryString(i, 4)));
        }
    }

    @Test
    void isCloseTo() {
        assertTrue(MathUtils.isCloseTo(1.0, 1.0));
        assertTrue(MathUtils.isCloseTo(1.0, 1.0 + MathUtils.EPSILON / 2));
        assertTrue(MathUtils.isCloseTo(1.0, 1.0 - MathUtils.EPSILON / 2));
        assertFalse(MathUtils.isCloseTo(1.0, 1.0 + MathUtils.EPSILON * 2));
    }

    @Test
    void wrap() {
        assertCloseTo(0.5, MathUtils.wrap(0, 1, 1.5));
        assertCloseTo(0.75, MathUtils.wrap(0, 1, -0.25));
        assertCloseTo(0, MathUtils.wrap(0, 10, 10));
        assertCloseTo(0, MathUtils.wrap(0, 10, 0));
        assertCloseTo(3, MathUtils.wrap(0, 10, 3));
        assertCloseTo(-Math.PI, MathUtils.wrap(-Math.PI, Math.PI, 3 * Math.PI));
        assertCloseTo(0, MathUtils.wrap(-Math.PI, Math.PI, 0));
    }

    @Test
    void wrap_invalidInterval() {
        assertThrows(IllegalArgumentException.class, () -> MathUtils.wrap(1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> MathUtils.wrap(2, 1, 0));
    }
}
