package problems;

import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.math3.distribution.UniformRealDistribution;

import locations.Location;
import model.CFSTP;

public class UniformProblem extends CFSTP {

	protected static final long serialVersionUID = 1L;

	protected boolean modified;

	protected UniformRealDistribution unif = new UniformRealDistribution(0, 50);

	protected float[] coalitionValues;

	public UniformProblem(int[] agents, int[] nodes, Location[] initialAgentLocations, Location[] nodeLocations, int[][] demands, boolean modified, int dynamismType) {
		super(agents, nodes, initialAgentLocations, nodeLocations, demands, dynamismType);
		this.modified = modified;

		coalitionValues = new float[agents.length];

		for (int i = 0; i < agents.length; i++) {
			if (modified) {
				coalitionValues[i] = (float) Math.abs(new UniformRealDistribution(0, 10 * (i+1)).sample());
				if (ThreadLocalRandom.current().nextInt(5) == 0) // probability 0.2
					coalitionValues[i] += (float) Math.abs(unif.sample());
			} else
				coalitionValues[i] = (float) Math.abs(new UniformRealDistribution(0, i+1).sample());
		}
	}

	@Override
	public float getCoalitionValue(int node, int[] coalition) {
		int i = coalition.length - 1;
		if (i < 0) i = 0;
		return coalitionValues[i];
	}

	@Override
	public float getAgentSpeed(int agent) {
		return 1;
	}

}