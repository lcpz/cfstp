package toolkit;

public class Utilities {

	/**
	 * Get an integer array with numbers from 0 to n-1.
	 *
	 * @param n The length of the array.
	 * @return The array
	 */
	public static Integer[] getRangeArray(int n) {
		if (n < 0)
			return null;

		if (n == 0)
			return new Integer[0];

		Integer[] arr = new Integer[n];

		for (int i = 0; i < n; i++)
			arr[i] = i;

		return arr;
	}

	public static int[] subarray(int[] a, int[] indexes) {
		if (a == null || indexes == null)
			return null;

		if (a.length == 0)
			return new int[0];

		if (indexes.length == 0)
			return a;

		int[] b = new int[indexes.length];

		for (int i = 0; i < indexes.length; i++)
			b[i] = a[indexes[i]];

		return b;
	}

	public static int[][] to2D(int[] m, int rowDim) {
		assert (m != null && rowDim > 0);

		/* recall that in Java, integer division returns an integer */
		double n = Math.ceil(m.length / (double) rowDim);

		int d1 = (int) n;
		int d2 = m.length % n == 0 ? d1 : rowDim;
		int[][] m2 = new int[d1][d2];
		int count = 0, i, j;

		for (i = 0; i < d1; i++)
			for (j = 0; j < d2; j++)
				if (count < m.length)
					m2[i][j] = m[count++];
				else
					return m2;

		return m2;
	}

	public static int getMax(int[] array) {
		int max = Integer.MIN_VALUE;
		for (int a : array)
			if (a > max)
				max = a;
		return max;
	}

	public static int sum(int[] array) {
		int sum = 0;
		for (int a : array)
			sum += a;
		return sum;
	}

}