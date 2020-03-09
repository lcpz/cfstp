package toolkit;

import java.util.Arrays;

import org.apache.commons.math3.util.Combinations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MathematicsTest {

	static int n = 20;

	@Test
	@DisplayName("Testing Mathematics#getCombinations")
	void test1() {
		for (int i = 1; i <= n; i++)
			for (int[] s : Mathematics.getCombinations(n, i))
				System.out.println(Arrays.toString(s));
	}

	@Test
	@DisplayName("Testing apache.commons.math3.util.Combinations")
	void test2() { /* should be faster than test1 */
		for (int i = 1; i <= n; i++)
			for (int[] s : new Combinations(n, i))
				System.out.println(Arrays.toString(s));
	}

}
