package problems;

import org.apache.commons.math3.distribution.UniformRealDistribution;

import locations.Location;
import model.CFSTP;

public class LinearPerturbedProblem extends CFSTP {

	protected static final long serialVersionUID = 1L;

	public final boolean perturbed;

	/* Taken from Ramchurn's 2010 CFSTP paper. */
	protected UniformRealDistribution unif = new UniformRealDistribution(1, 2);

	float[] cValues;

	public LinearPerturbedProblem(int[] agents, int[] nodes, Location[] initialAgentLocations, Location[] nodeLocations, int[][] demands, boolean perturbed, int dynamismType) {
		super(agents, nodes, initialAgentLocations, nodeLocations, demands, dynamismType);
		this.perturbed = perturbed;
		if (perturbed) {
			cValues = new float[agents.length];
			for (int i = 0; i < agents.length; i++)
				cValues[i] = (float) ((i+1) * Math.abs(unif.sample()));
		}
	}

	@Override
	public float getCoalitionValue(int node, int[] coalition) {
		if (!perturbed) // superadditive
			return (float) coalition.length;
		else {
			int i = coalition.length - 1;
			if (i < 0) i = 0;
			return cValues[i];
		}
	}

	@Override
	public float getAgentSpeed(int agent) {
		return 1;
	}

}