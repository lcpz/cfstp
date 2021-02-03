package problems;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.math3.distribution.UniformRealDistribution;

import locations.Location;
import model.CFSTP;

public class UCAgentBasedProblem extends CFSTP {

	protected static final long serialVersionUID = 1L;

	protected Map<Integer, Float> agentPerformance;

	public final boolean perturbed, urgent;

	protected Map<String, Float> coalitionValueMap;

	public UCAgentBasedProblem(int[] agents, int[] nodes, Location[] initialAgentLocations, Location[] nodeLocations, int[][] demands, boolean perturbed, boolean urgent, int dynamismType) {
		super(agents, nodes, initialAgentLocations, nodeLocations, demands, dynamismType);
		this.perturbed = perturbed;
		this.urgent = urgent;

		agentPerformance = new HashMap<>();

		// pre-computing
		UniformRealDistribution d = new UniformRealDistribution(0, 10);
		for (int a : agents)
			agentPerformance.put(a, (float) Math.abs(new UniformRealDistribution(0, 2 * Math.abs(d.sample())).sample()));

		coalitionValueMap = new HashMap<>();
	}

	@Override
	public float getCoalitionValue(int node, int[] coalition) {
		String s = String.format("%d%s", node, Arrays.toString(coalition));
		Float fmap = coalitionValueMap.get(String.format("%d%s", node, Arrays.toString(coalition)));

		if (fmap != null)
			return fmap.floatValue();

		float f = 0f;

		for (int a : coalition)
			f += agentPerformance.get(a);

		if (perturbed || urgent) {
			int probability;
			UniformRealDistribution r = new UniformRealDistribution(f/10, f/4);

			if (perturbed) {
				probability = (int) Math.ceil(coalition.length / (double) (agents.length + 1)) * 100;
				if (ThreadLocalRandom.current().nextInt(101) <= probability)
					f -= r.sample();
			}

			if (urgent) {
				probability = (int) Math.ceil(demands[node][0] / (double) (maximumProblemCompletionTime + 1)) * 100;
				if (ThreadLocalRandom.current().nextInt(101) <= probability)
					f -= r.sample();
			}
		}

		coalitionValueMap.put(s, f);

		return f;
	}

	@Override
	public float getAgentSpeed(int agent) {
		return 1;
	}

}