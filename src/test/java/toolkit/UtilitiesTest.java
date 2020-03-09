package toolkit;

import java.util.Arrays;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UtilitiesTest {

	static final int DIM = 1000;
	static final int[] m = ArrayUtils.toPrimitive(Utilities.getRangeArray(DIM * DIM));

	static void toStdout(int[][] m2) {
		for (int[] row : m2)
			System.out.println(Arrays.toString(row));
		System.out.println();
	}

	@Test
	@DisplayName("Testing Utilities#to2D with a divisor number of columns")
	void test1() {
		toStdout(Utilities.to2D(m, DIM));
	}

	@Test
	@DisplayName("Testing Utilities#to2D with a non-divisor number of columns")
	void test2() {
		toStdout(Utilities.to2D(m, DIM - 1));
	}

}
