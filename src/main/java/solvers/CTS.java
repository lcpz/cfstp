package solvers;

import java.util.Arrays;
import java.util.Comparator;

import org.apache.commons.lang3.ArrayUtils;

import model.CFSTP;

/**
 * Cluster-based Node Scheduling (CTS) algorithm for solving CFSTPs.
 *
 * @author lcpz
 */
public class CTS extends Solver {

	/**
	 * Data structure containing a feasible coalition allocation.
	 *
	 * @author lcpz
	 */
	public class FeasibleAllocation {

		public final int node, allocationTime;
		public final int[] agents, arrivalTimes;

		public FeasibleAllocation(int allocationTime, int node, int[] agents, int[] arrivalTimes) {
			this.allocationTime = allocationTime;
			this.node = node;
			this.agents = agents;
			this.arrivalTimes = arrivalTimes;
		}

	}

	/* Each CTS message contains:
	 * 1. A node address (8 bytes in Java).
	 * 2. A boolean/message flag (1 byte).
	 *
	 * To which we have to add an integer value representing a time unit (1-4 bytes).
	 */
	public static final int BASE_MESSAGE_SIZE = 9;

	public CTS(CFSTP problem) {
		super(problem);
	}

	@Override
	public int solve(int t) {
		phaseOne();
		return phaseTwo(); // returns the number of nodes whose visit is completed at time t
	}

	protected void phaseOne() {
		for (int a = 0; a < agents.length; a++) {
			/* if the problem is static or the agent is enabled */
			if (problem.dynamismType == 0 || (problem.dynamismType == 1 && problem.enabled[a]) || problem.dynamismType == 2) {
				/* if possible, allocate a to a node */
				if (agentStatus[a] == AgentStatus.FREE) {
					int v = getNodeAllocableToAgent(a);
					if (v > -1) {
						assignmentStatus[a][v] = AssignmentStatus.FEASIBLE;
						nodeStatus[v] = NodeStatus.ALLOCABLE;
						messagesSent++;
						if (DEBUG)
							System.out.println(String.format("agent %d can reach node %d", a, v));
					}
				/* otherwise, if a reached its assigned node, update its status */
				} else if (reachingNode[a][0] > -1 && assignmentStatus[a][reachingNode[a][0]] == AssignmentStatus.REACHING)
					updateAgentStatus(a);
			} else if (problem.dynamismType == 1 && reachingNode[agents[a]][0] > -1) // remove a from the system
				removeAgent(a);
		}
	}

	/**
	 * Given agent a, return the current closest and uncompleted/allocated node v
	 * reachable by a.
	 *
	 * @param a An agent index.
	 *
	 * @return A node index.
	 */
	protected int getNodeAllocableToAgent(int a) {
		int bestNode[] = new int[] { -1, -1 };
		int bestDeadline[] = new int[] { maximumProblemCompletionTime + 1, maximumProblemCompletionTime + 1 };
		int bestArrivalTime[] = new int[] { maximumProblemCompletionTime + 1, maximumProblemCompletionTime + 1 };
		int idx, arrivalTime;

		for (int v = 0; v < nodes.length; v++)
			if (nodeStatus[v] == NodeStatus.NOT_COMPLETED || nodeStatus[v] == NodeStatus.ALLOCATED) {
				idx = 0;
				if (nodeStatus[v] == NodeStatus.ALLOCATED)
					idx = 1;
				arrivalTime = currentTime + agentLocations[a].getTravelTimeTo(nodeLocations[v], problem.getAgentSpeed(a));
				if (arrivalTime < demands[v][0] && demands[v][0] < bestDeadline[idx]
						&& arrivalTime < bestArrivalTime[idx]) {
					bestDeadline[idx] = demands[v][0];
					bestArrivalTime[idx] = arrivalTime;
					bestNode[idx] = v;
				}
			}

		if (bestNode[0] != -1) { // prioritise unallocated nodes
			networkLoad += Solver.bytesNeeded(bestArrivalTime[0]) + CTS.BASE_MESSAGE_SIZE;
			return bestNode[0];
		}

		if (bestNode[1] != -1)
			networkLoad += Solver.bytesNeeded(bestArrivalTime[1]) + CTS.BASE_MESSAGE_SIZE;

		return bestNode[1];
	}

	protected int phaseTwo() {
		int numberOfVisitedNodesNow = 0;
		int[] workers, reachers;
		int i, j, a;
		StringBuilder s;

		for (int v = 0; v < nodes.length; v++) {
			if (problem.dynamismType < 2 || (problem.dynamismType == 2 && problem.enabled[v])) {
				if (nodeStatus[v] == NodeStatus.ALLOCABLE)
					computeAndAllocateCoalition(v);

				if (nodeStatus[v] == NodeStatus.ALLOCATED) { // update node workload
					if (DEBUG)
						s = new StringBuilder(String.format("%4d workload: %4.2f", v, workloads[v]).replace(",", "."));

					workers = new int[visitingNode[v]];
					reachers = new int[agents.length];
					i = 0;
					j = 0;
					for (a = 0; a < agents.length; a++)
						switch (assignmentStatus[a][v]) {
						case VISITING:
							workers[i++] = a;
							break;
						case REACHING:
							reachers[j++] = a;
							break;
						default:
							break;
						}

					if (j > 0) {
						reachers = ArrayUtils.subarray(reachers, 0, j);
						if (DEBUG)
							s.append(String.format(", reaching: %3d", reachers.length));
					}

					if (i > 0) {
						if (DEBUG)
							s.append(String.format(", working: %3d", workers.length));

						/* Decrease w_v by u(C, v). */
						workloads[v] -= problem.getCoalitionValue(v, workers);

						if (workloads[v] <= 0) {
							if (DEBUG)
								s.append(", done \u2713");

							//coalitionSizes.add(Integer.valueOf(workers.length));
							visitingNode[v] = 0;
							nodeStatus[v] = NodeStatus.COMPLETED;
							numberOfVisitedNodesNow++;
							for (a = 0; a < workers.length; a++) {
								assignmentStatus[a][v] = AssignmentStatus.DONE;
								agentStatus[a] = AgentStatus.FREE;
								isBusyAgent[a] = false;
							}
							/* Line 7 in Algorithm 2 of D-CTS */
							networkLoad += (reachers.length + workers.length) * (Solver.bytesNeeded(currentTime) + BASE_MESSAGE_SIZE);
						}
					}

					// nobody reaching nor working on v, but v might be still allocable
					if (i == 0 && j == 0 && currentTime < demands[v][0]) {
						isAllocatedNode[v] = false;
						nodeStatus[v] = NodeStatus.NOT_COMPLETED; // next t verifies if it is allocable
					}

					if (DEBUG)
						System.out.println(s.toString());
				}
			}
		}

		if (problem.dynamismType == 0)
			stoppingCondition = allAgentsAreAvailable();

		return numberOfVisitedNodesNow;
	}

	protected void computeAndAllocateCoalition(int v) {
		FeasibleAllocation feasibleAllocation = getFeasibleAgentsByArrivalTime(v);
		int[] feasibleAgents = feasibleAllocation.agents;
		int[] agentsVisitingNode = getAgentsVisitingNode(v);
		int[] arrivalTimes = feasibleAllocation.arrivalTimes;
		int[] subCoalition = null;
		float cValue;
		int i;
		float workloadDone = 0f;

		for (i = 0; i < feasibleAgents.length; i++) {
			/*
			 * If multiple agents arrive at the same time, consider only the last one in the
			 * order.
			 */
			if (i + 1 < feasibleAgents.length && arrivalTimes[i] == arrivalTimes[i + 1])
				continue;

			subCoalition = ArrayUtils.subarray(feasibleAgents, 0, i + 1);

			if (agentsVisitingNode.length > 0)
				cValue = problem.getCoalitionValue(v,
						ArrayUtils.addAll(agentsVisitingNode, subCoalition));
			else
				cValue = problem.getCoalitionValue(v, subCoalition);

			if (i + 1 < feasibleAgents.length)
				workloadDone += (arrivalTimes[i + 1] - arrivalTimes[i]) *
					problem.getCoalitionValue(v, ArrayUtils.addAll(subCoalition, agentsVisitingNode));

			NCCCs++; // due to the following conditional

			/* If coalition of first i agents can complete v within deadline, stop. */
			if (cValue * (demands[v][0] - arrivalTimes[i]) >= workloads[v] - workloadDone)
				break;
		}

		while (++i < feasibleAgents.length)
			assignmentStatus[feasibleAgents[i]][v] = AssignmentStatus.NONE;

		allocate(v, subCoalition, arrivalTimes);

		messagesSent += subCoalition.length;
	}

	/**
	 * Given a node v, get the agents that can reach v at current time, and return
	 * them sorted by arrival time to v.
	 *
	 * @param v The Node.
	 *
	 * @return a feasible allocation (i.e., the above agents plus related useful
	 *         information)
	 */
	protected FeasibleAllocation getFeasibleAgentsByArrivalTime(int v) {
		/* get feasible agents */
		int[] feasibleAgents = new int[agents.length];

		int i = 0;

		for (int a = 0; a < agents.length; a++)
			if (assignmentStatus[a][v] == AssignmentStatus.FEASIBLE)
				feasibleAgents[i++] = a;

		if (i == 0)
			return null;

		feasibleAgents = ArrayUtils.subarray(feasibleAgents, 0, i);

		/* get arrival times */
		int[] arrivalTimes = new int[feasibleAgents.length];
		Integer[] indexes = new Integer[arrivalTimes.length];

		for (i = 0; i < arrivalTimes.length; i++) {
			arrivalTimes[i] = currentTime + agentLocations[feasibleAgents[i]].getTravelTimeTo(nodeLocations[v], problem.getAgentSpeed(feasibleAgents[i]));
			indexes[i] = i;
		}

		/* sort both arrays by arrival times */
		Arrays.sort(indexes, new Comparator<Integer>() {
			public int compare(Integer a, Integer b) {
				if (arrivalTimes[a.intValue()] < arrivalTimes[b.intValue()])
					return -1;
				if (arrivalTimes[a.intValue()] == arrivalTimes[b.intValue()])
					return 0;
				return 1;
			}
		});

		int[] sortedFeasibleAgents = new int[feasibleAgents.length];
		int[] sortedArrivalTimes = new int[arrivalTimes.length];

		for (i = 0; i < indexes.length; i++) {
			sortedFeasibleAgents[i] = feasibleAgents[indexes[i]];
			sortedArrivalTimes[i] = arrivalTimes[indexes[i]];
		}

		return new FeasibleAllocation(currentTime, v, sortedFeasibleAgents, sortedArrivalTimes);
	}

	protected int[] getAgentsVisitingNode(int v) {
		if (visitingNode[v] <= 0)
			return ArrayUtils.EMPTY_INT_ARRAY;

		int[] workers = new int[agents.length];
		int i = 0;

		for (int a = 0; a < agents.length; a++)
			if (assignmentStatus[a][v] == AssignmentStatus.VISITING)
				workers[i++] = a;

		return ArrayUtils.subarray(workers, 0, i);
	}

}
