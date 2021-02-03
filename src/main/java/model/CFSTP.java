package model;

import java.io.Serializable;

import org.apache.commons.lang3.SerializationUtils;

import locations.Location;

/**
 * Model of the Coalition Formation with Spatial and Temporal constraints Problem (CFSTP).
 *
 * @author lcpz
 */
public abstract class CFSTP implements Serializable {

	private static final long serialVersionUID = 1L;

	/* Nodes and agents are uniquely identified by integers */
	public final int[] nodes, agents;

	/* Node locations (static) are 2-dimensional points: [nodeId][x,y] */
	public final Location[] nodeLocations;

	/* Likewise, initial agent locations are 2-dimensional points: [agentId][x,y] */
	public final Location[] initialAgentLocations;

	/*
	 * Node demands: [deadline, workload].
	 *
	 * Row of index <code>i</code> contains the demands of <code>nodes[i]</code>.
	 *
	 * Since time units are integers, the workload is rounded to the next integer.
	 */
	public final int[][] demands;

	/* Maximum problem completion time */
	public final int maximumProblemCompletionTime;

	/* Flag array defining which agents/nodes are currently enabled */
	public final boolean[] enabled;

	/**
	 * 0 - Static problem
	 * 1 - Agents might decrease with time
	 * 2 - Nodes might increase with time
	 */
	public final int dynamismType;

	private int enabledIdx, dynamismStep;

	public CFSTP(int[] agents, int[] nodes, Location[] initialAgentLocations, Location[] nodeLocations, int[][] demands, int dynamismType) {
		this.agents = agents;
		this.nodes = nodes;
		this.nodeLocations = nodeLocations;
		this.demands = demands;

		int i, mpct = 0;

		try {
			for (i = 0; i < demands.length; i++) {
				if (demands[i][0] < 0)
					throw new Exception(String.format("node %d has deadline = %d", i, demands[i][0]));
				if (demands[i][1] < 0)
					throw new Exception(String.format("node %d has workload = %d", i, demands[i][1]));

				if (demands[i][0] > mpct)
					mpct = demands[i][0];
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		maximumProblemCompletionTime = mpct;

		/* setting initial agent locations */
		this.initialAgentLocations = initialAgentLocations;

		/* setting dynamism type */
		if (dynamismType < 0 || dynamismType > 2)
			dynamismType = 0;

		if (dynamismType == 1) {
			enabled = new boolean[agents.length];
			for (i = 0; i < enabled.length; i++)
				enabled[i] = true; // start with all agents enabled
			dynamismStep = (int) Math.ceil(maximumProblemCompletionTime / (float) agents.length);
		} else {
			enabled = new boolean[nodes.length];
			enabled[0] = true; // start with 1 enabled node
			dynamismStep = (int) Math.ceil((float) nodes.length / (maximumProblemCompletionTime / 2));
			enabledIdx = 1;
		}

		this.dynamismType = dynamismType;
	}

	/**
	 * Set the current time.
	 *
	 * Only used by Dynamic CFSTPs.
	 *
	 * @param t Current time.
	 */
	public void setTime(int t) {
		if (dynamismType == 0 || t < 1)
			return; // the problem is static or we do not need to change it now

		/*
		 * Decrease agents/increase nodes following a Poisson CDF where:
		 * 1. x = DateAndTimeArrived column for agents and
		 * 2. x = TimeOfCall column for nodes.
		 *
		 * The time interval spans from January 2009 to December 2020.
		 *
		 * \lambda = avg(x) expresses the average rate of events per hour and
		 * per day.
		 */
		double timePercentage = t / (double) maximumProblemCompletionTime;

		if (dynamismType == 1 && 0.15 <= timePercentage && timePercentage <= 0.7) {
			// decrease uniformly for timePercentage \in [0.15, 0.7]
			if (t % dynamismStep == 0 && enabledIdx + 1 < enabled.length)
				enabled[enabledIdx++] = false;
		} else if (dynamismType == 2 && timePercentage <= 0.5) {
			// increase uniformly for timePercentage \in [0, 0.5], such that at timePercentage >= 0.5 all nodes are enabled
			int howManyPerTimeUnit = dynamismStep;
			while (howManyPerTimeUnit-- > 0)
				if (enabledIdx + 1 < enabled.length)
					enabled[enabledIdx++] = true;
				else
					break;
		}
	}

	public int getCurrentNumberOfAgents() {
		if (dynamismType == 1)
			return agents.length - enabledIdx - 1;
		return agents.length;
	}

	public int getCurrentNumberOfNodes() {
		if (dynamismType == 2)
			return enabledIdx + 1;
		return nodes.length;
	}

	/**
	 * Given node $v$ and coalition $C \in 2^A$, the coalition value of $C$ $u(C)$
	 * determines the amount of workload that $C$ does in a time step.
	 *
	 * @param v      The node <code>v</code>.
	 * @param coalition A coalition assigned to <code>v</code>.
	 *
	 * @return The value of <code>coalition</code.
	 */
	public abstract float getCoalitionValue(int v, int[] coalition);

	/**
	 * Determines the speed of the input agent.
	 *
	 * @param an Agent ID.
	 * @return a speed.
	 */
	public abstract float getAgentSpeed(int agent);

	/**
	 * Return a deep copy of this problem instance.
	 */
	public CFSTP clone() {
		return (CFSTP) SerializationUtils.clone(this);
	}

}