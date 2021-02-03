package toolkit;

import java.util.HashMap;
import java.util.List;

public class Utils {

	public static int[] list2Array(List<Integer> l) {
		int[] arr = new int[l.size()];
		int i = 0;

		for (Integer v : l)
			arr[i++] = v.intValue();

		return arr;
	}

	public static int[] char2IntArray(char[] l) {
		int[] arr = new int[l.length];

		for (int i = 0; i < l.length; i++)
			arr[i] = Character.getNumericValue(l[i]);

		return arr;
	}

	public static HashMap<Integer, Float> getZeroMessages(List<Integer> domain) {
		HashMap<Integer, Float> map = new HashMap<>();

		for (int d : domain)
			map.put(d, 0f);

		return map;
	}

	public static float checkedSum(float a, float b) {
		float sum = a + b;

		if (Float.isFinite(sum))
			return sum;
		else
			return Float.POSITIVE_INFINITY;
	}

	public static double[] intList2DoubleArray(List<Integer> coalitionSizes) {
		double[] arr = new double[coalitionSizes.size()];

		int i = 0;
		for (Integer cs : coalitionSizes)
			arr[i++] = cs.intValue();

		return arr;
	}

}