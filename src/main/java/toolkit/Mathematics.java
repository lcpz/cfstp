package toolkit;

import java.util.LinkedList;

public class Mathematics {

	private static int[] printBits(int u, int c) {
		int idx = 0;
		int[] com = new int[c];
		for (int n = 0; u > 0; ++n, u >>= 1)
			if ((u & 1) > 0)
				com[idx++] = n;
		return com;
	}

	private static int countBits(int u) {
		int n;
		for (n = 0; u > 0; ++n, u &= (u - 1)); /* Turn the last set bit to a 0 */
		return n;
	}

	/* Gray binary code - https://rosettacode.org/wiki/Combinations#Java */
	public static LinkedList<int[]> getCombinations(int n, int c) {
		LinkedList<int[]> s = new LinkedList<>();
		for (int u = 0; u < 1 << n+1; u++)
			if (countBits(u) == c)
				s.add(printBits(u, c));
		return s;
	}

	/* get the normalisation of x in [min, max] */
	public static float getZ(int x, int min, int max) {
		return (x - min) / (float) (max - min);
	}

}