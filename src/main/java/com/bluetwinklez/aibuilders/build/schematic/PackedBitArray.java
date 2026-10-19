package com.bluetwinklez.aibuilders.build.schematic;

/**
 * Fixed-width integers packed into longs the way Litematica stores block states: entries are
 * written back to back, so a single entry may span two longs (unlike vanilla's padded
 * {@code SimpleBitStorage}).
 */
public final class PackedBitArray {
	private final long[] data;
	private final int bits;
	private final long mask;
	private final long size;

	public PackedBitArray(int bits, long size) {
		this(bits, size, new long[(int) Math.ceil(size * bits / 64.0)]);
	}

	public PackedBitArray(int bits, long size, long[] data) {
		if (bits < 1 || bits > 32) {
			throw new IllegalArgumentException("bits must be 1..32, got " + bits);
		}
		long needed = (long) Math.ceil(size * bits / 64.0);
		if (data.length < needed) {
			throw new IllegalArgumentException("Need " + needed + " longs for " + size + " entries of " + bits + " bits, got " + data.length);
		}
		this.data = data;
		this.bits = bits;
		this.mask = (1L << bits) - 1L;
		this.size = size;
	}

	/** Bits per entry Litematica uses for a palette of the given size (minimum 2). */
	public static int bitsForPaletteSize(int paletteSize) {
		return Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(Math.max(1, paletteSize) - 1));
	}

	public int get(long index) {
		checkIndex(index);
		long startOffset = index * bits;
		int startArr = (int) (startOffset >> 6);
		int endArr = (int) (((index + 1) * bits - 1) >> 6);
		int startBit = (int) (startOffset & 0x3F);
		if (startArr == endArr) {
			return (int) (data[startArr] >>> startBit & mask);
		}
		int endOffset = 64 - startBit;
		return (int) ((data[startArr] >>> startBit | data[endArr] << endOffset) & mask);
	}

	public void set(long index, int value) {
		checkIndex(index);
		if ((value & ~mask) != 0) {
			throw new IllegalArgumentException("Value " + value + " does not fit in " + bits + " bits");
		}
		long startOffset = index * bits;
		int startArr = (int) (startOffset >> 6);
		int endArr = (int) (((index + 1) * bits - 1) >> 6);
		int startBit = (int) (startOffset & 0x3F);
		data[startArr] = data[startArr] & ~(mask << startBit) | ((long) value & mask) << startBit;
		if (startArr != endArr) {
			int endOffset = 64 - startBit;
			int spill = bits - endOffset;
			data[endArr] = data[endArr] >>> spill << spill | ((long) value & mask) >> endOffset;
		}
	}

	public long size() {
		return size;
	}

	public int bits() {
		return bits;
	}

	public long[] data() {
		return data;
	}

	private void checkIndex(long index) {
		if (index < 0 || index >= size) {
			throw new IndexOutOfBoundsException(index + " not in [0, " + size + ")");
		}
	}
}
