package com.bluetwinklez.aibuilders.build.schematic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PackedBitArrayTest {
	@Test
	void bitsForPaletteSizeHasMinimumOfTwo() {
		assertEquals(2, PackedBitArray.bitsForPaletteSize(1));
		assertEquals(2, PackedBitArray.bitsForPaletteSize(2));
		assertEquals(2, PackedBitArray.bitsForPaletteSize(4));
		assertEquals(3, PackedBitArray.bitsForPaletteSize(5));
		assertEquals(5, PackedBitArray.bitsForPaletteSize(17));
		assertEquals(5, PackedBitArray.bitsForPaletteSize(32));
		assertEquals(6, PackedBitArray.bitsForPaletteSize(33));
	}

	@ParameterizedTest
	@ValueSource(ints = {2, 3, 5, 7, 13, 17, 31})
	void roundTripsIncludingEntriesSpanningTwoLongs(int bits) {
		int size = 1000;
		PackedBitArray array = new PackedBitArray(bits, size);
		Random random = new Random(bits);
		int[] expected = new int[size];
		for (int i = 0; i < size; i++) {
			expected[i] = random.nextInt((int) Math.min(1L << bits, Integer.MAX_VALUE));
			array.set(i, expected[i]);
		}
		int[] actual = new int[size];
		for (int i = 0; i < size; i++) {
			actual[i] = array.get(i);
		}
		assertArrayEquals(expected, actual);
	}

	@Test
	void matchesLitematicaLayoutForThreeBitEntries() {
		// With 3 bits per entry, entry 21 occupies bits 63..65: one bit in long 0, two in long 1.
		long[] data = new long[2];
		int value = 0b101;
		data[0] |= (long) (value & 1) << 63;
		data[1] |= value >>> 1;
		PackedBitArray array = new PackedBitArray(3, 42, data);
		assertEquals(value, array.get(21));
		assertEquals(0, array.get(20));
		assertEquals(0, array.get(22));
	}

	@Test
	void settingDoesNotDisturbNeighbours() {
		PackedBitArray array = new PackedBitArray(5, 64);
		for (int i = 0; i < 64; i++) {
			array.set(i, 31);
		}
		array.set(12, 0);
		for (int i = 0; i < 64; i++) {
			assertEquals(i == 12 ? 0 : 31, array.get(i), "index " + i);
		}
	}

	@Test
	void rejectsShortDataAndOutOfRange() {
		assertThrows(IllegalArgumentException.class, () -> new PackedBitArray(4, 100, new long[2]));
		PackedBitArray array = new PackedBitArray(2, 10);
		assertThrows(IndexOutOfBoundsException.class, () -> array.get(10));
		assertThrows(IllegalArgumentException.class, () -> array.set(0, 4));
	}
}
