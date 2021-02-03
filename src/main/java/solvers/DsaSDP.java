package solvers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.lang3.ArrayUtils;

import locations.Location;
import model.CFSTP;
import toolkit.Utils;

/**
 * Distributed Stochastic Algorithm with Slope Dependent Probability (DSA-SDP).
 *
 * @author lcpz
 */
public class DsaSDP extends Solver {

	// Taken from (Zivan et al., 2014)
	public final float P_A = 0.6f;
	public final float P_B = 0.15f;
	public final float P_C = 0.4f;
	public final float P_D = 0.8f;

	public class ValueAssignment {
		public final Integer agentIdx;
		public final List<Integer> domain; // list of node indexes
		public Integer assignment;

		public ValueAssignment(Integer agentIdx, List<Integer> domain) {
			this.agentIdx = agentIdx;
			this.domain = domain;
		}

		public Integer setRandomValue() {
			int idx = ThreadLocalRandom.current().nextInt(domain.size());
			assignment = domain.get(idx);
			return assignment;
		}
	}

	public DsaSDP(CFSTP problem) {
		super(problem);
	}

	@Override
	public int solve(int t) {
		/*
		 * 1. Create variable domains, thus satisfying structural and spatial
		 * constraints.
		 *
		 * We use agent indexes instead of the actual IDs.
		 */
		List<ValueAssignment> freeAgentVAs = new ArrayList<>();
		List<Integer> freeAgentDomain;
		int allocableAgents = 0;
		for (int a = 0; a < agents.length; a++) {
			/* if the problem is static or the agent is enabled */
			if (problem.dynamismType == 0 || (problem.dynamismType == 1 && problem.enabled[a]) || problem.dynamismType == 2) {
				/* compute the variable domain of each free agent */
				if (agentStatus[a] == AgentStatus.FREE) {
					freeAgentDomain = getFreeAgentDomain(a);

					if (freeAgentDomain.size() == 0)
						continue; // this free agent cannot reach any node v by deadline d_v

					freeAgentVAs.add(new ValueAssignment(Integer.valueOf(a), freeAgentDomain));

					allocableAgents++;
				}
				/* otherwise, if a reached its assigned node, update its status */
				else if (reachingNode[a][0] > -1 && assignmentStatus[a][reachingNode[a][0]] == AssignmentStatus.REACHING)
					updateAgentStatus(a);
			} else if (problem.dynamismType == 1 && reachingNode[agents[a]][0] > -1) // remove a from the system
				removeAgent(a);
		}

		if (allocableAgents > 0) {
			Integer[] assignments = new Integer[freeAgentVAs.size()];
			int i, j;
			int freeAgentNr = freeAgentVAs.size();
			int messagesSentNow;
			ValueAssignment va;

			/* 2. Initial random value assignment for each agent. */
			for (i = 0; i < freeAgentNr; i++)
				assignments[i] = freeAgentVAs.get(i).setRandomValue();

			/*
			 * 3. Compute coalition allocations.
			 *
			 * We iterate only once, instead of 1000 times like in the original
			 * DSA paper. This is because we found that, in the LFB benchmark,
			 * having more than one iteration can only marginally improve the
			 * percentage of completed tasks, at the cost of increasing the DCOP
			 * metrics exponentially.
			 */

			// DCOP metrics
			messagesSentNow = freeAgentNr * (freeAgentNr - 1);
			messagesSent += messagesSentNow;
			networkLoad += messagesSentNow * 8; // 8 is the number of bytes required to store a node address

			/*
			 * Each loop iteration is done in parallel, hence the NCCCs comes from
			 * evaluating the constraints with each new agent value assignment.
			 */
			for (j = 0; j < freeAgentNr; j++) { // for each free agent
				va = freeAgentVAs.get(j);
				assignments[j] = updateOrConfirmAssignment(freeAgentVAs, assignments, va);
			}

			/*
			 * 4. Allocate nodes to agents and update each agent status, thus
			 * satisfying the structural constraints.
			 */
			HashMap<Integer, List<Integer>> coalitionAllocations = new HashMap<>();
			List<Integer> coalition;
			for (i = 0; i < assignments.length; i++) {
				coalition = coalitionAllocations.get(assignments[i]);
				if (coalition == null)
					coalition = new ArrayList<>();
				coalition.add(freeAgentVAs.get(i).agentIdx);
				coalitionAllocations.put(assignments[i], coalition);
			}

			int[] coalitionArray, arrivalTimes;
			int nodeIdx;
			for (Map.Entry<Integer, List<Integer>> entry : coalitionAllocations.entrySet()) {
				nodeIdx = entry.getKey().intValue();
				coalitionArray = Utils.list2Array(entry.getValue());
				arrivalTimes = getArrivalTimes(coalitionArray, nodeIdx);
				allocate(nodeIdx, coalitionArray, arrivalTimes);
				//coalitionSizes.add(Integer.valueOf(coalitionArray.length));

				if (DEBUG)
					System.out.println(String.format("Allocating node %d to coalition %s", nodeIdx, entry.getValue().toString()));
			}
		}

		return updateNodeStatus(); // returns how many nodes have been visited now
	}

	protected Integer updateOrConfirmAssignment(List<ValueAssignment> freeAgentVAs, Integer[] assignments, ValueAssignment va) {
		/*
		 * Get the coalition allocation related to va.assignment, which is the
		 * node currently allocated to the agent with index va.agentIdx.
		 */
		ArrayList<Integer> coalitionList = new ArrayList<>();
		for (int i = 0; i < assignments.length; i++)
			if (assignments[i].equals(va.assignment))
				coalitionList.add(freeAgentVAs.get(i).agentIdx);

		Float currentCost = evaluateTemporalConstraints(va.assignment, coalitionList);
		Number[] newCostAssignment = getNewCost(freeAgentVAs, va);
		Float newCost = (Float) newCostAssignment[0];

		if (newCost == Float.POSITIVE_INFINITY)
			return va.assignment; // current assignment is the best

		float pCost = Math.abs(currentCost.floatValue() - newCost.floatValue()) / currentCost.floatValue();
		float p = P_A + Math.min(P_B, pCost);
		float q = Math.max(P_C, P_D - pCost);

		boolean condA = ThreadLocalRandom.current().nextInt(101) <= p * 100;
		boolean condB = pCost <= 1 && ThreadLocalRandom.current().nextInt(101) <= q * 100;

		if (condA || condB)
			return (Integer) newCostAssignment[1]; // new assignment

		return va.assignment;
	}

	private Number[] getNewCost(List<ValueAssignment> freeAgentVAs, ValueAssignment va) {
		ArrayList<Integer> coalitionList;
		Float otherCost;

		Float minCost = Float.POSITIVE_INFINITY;
		Integer bestAssignment = null;

		for (Integer otherAssignment : va.domain) {
			coalitionList = new ArrayList<>();
			if (!otherAssignment.equals(va.assignment)) {
				// get all agent with the same assignment
				for (ValueAssignment va2: freeAgentVAs)
					if (va2.assignment.equals(va.assignment))
						coalitionList.add(va2.agentIdx);
			}

			otherCost = evaluateTemporalConstraints(otherAssignment, coalitionList);

			if (otherCost < minCost) { // assuming a minimisation problem
				minCost = otherCost;
				bestAssignment = otherAssignment;
			}
		}

		return new Number[] {minCost, bestAssignment};
	}

	private Float evaluateTemporalConstraints(Integer nodeIdx, List<Integer> coalitionList) {
		if (coalitionList.isEmpty())
			return Float.POSITIVE_INFINITY;

		int i;

		/* 1. Get agent arrival times to nodeIdx. */
		int[] coalition = Utils.list2Array(coalitionList);
		int[] arrivalTimes = new int[coalition.length];
		Integer[] indexes = new Integer[arrivalTimes.length];

		Location nodeLocation = nodeLocations[nodeIdx.intValue()];
		Location agentLocation;
		for (i = 0; i < arrivalTimes.length; i++) {
			agentLocation = agentLocations[coalition[i]];
			arrivalTimes[i] = currentTime + agentLocation.getTravelTimeTo(nodeLocation, problem.getAgentSpeed(coalition[i]));
			indexes[i] = i;
		}

		/* 2. Sort agents by arrival time to nodeIdx. */
		Arrays.sort(indexes, new Comparator<Integer>() {
			public int compare(Integer a, Integer b) {
				if (arrivalTimes[a.intValue()] < arrivalTimes[b.intValue()])
					return -1;
				if (arrivalTimes[a.intValue()] == arrivalTimes[b.intValue()])
					return 0;
				return 1;
			}
		});

		int[] sortedCoalition = new int[coalition.length];
		int[] sortedArrivalTimes = new int[arrivalTimes.length];

		for (i = 0; i < indexes.length; i++) {
			sortedCoalition[i] = coalition[indexes[i]];
			sortedArrivalTimes[i] = arrivalTimes[indexes[i]];
		}

		/* 3. Check if temporal constraints are satisfied. */
		float workload = workloads[nodeIdx.intValue()];
		float workloadDone = 0f, subCoalitionWorkloadDone, cValue;
		int[] subCoalition;
		int interval;
		for (i = 0; i < sortedCoalition.length; i++) {
			/*
			 * If multiple agents arrive at the same time, consider only the last one in the
			 * order.
			 */
			if (i + 1 < sortedCoalition.length && arrivalTimes[i] == arrivalTimes[i + 1])
				continue;

			subCoalition = ArrayUtils.subarray(sortedCoalition, 0, i + 1);

			if (i + 1 < sortedCoalition.length)
				interval = arrivalTimes[i + 1] - arrivalTimes[i];
			else
				interval = getDeadline(nodeIdx.intValue()) - arrivalTimes[i];

			cValue = problem.getCoalitionValue(nodeIdx.intValue(), subCoalition);
			subCoalitionWorkloadDone = interval * cValue;

			if (workloadDone + subCoalitionWorkloadDone >= workload) { // constraints satisfied: return minimum t
				NCCCs += i;
				return Float.valueOf(arrivalTimes[i] + (float) Math.ceil((workload - workloadDone) / cValue));
			} else
				workloadDone += subCoalitionWorkloadDone;
		}

		return Float.POSITIVE_INFINITY; // constraints not satisfied
	}

}