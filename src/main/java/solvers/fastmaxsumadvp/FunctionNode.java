package solvers.fastmaxsumadvp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.util.Combinations;

import locations.Location;
import solvers.Solver;
import toolkit.Utils;

public class FunctionNode {

	/**
	 * Limits the number of Q messages computed to improve the computational
	 * time.
	 */
	public final boolean LIMITED = false; // 1 set of Q messages per possible coalition size

	public final int nodeIdx;
	public final Map<Integer, Integer> scopeMap; // agent indexes
	public final int[] scope;

	/* Last messages sent to each agent in the scope of this function. */
	private Float[][] R; // row: agentIdx, column: 0 (agentIdx is not assigned), 1 (it is)

	/* The value selections of the agents in the scope of this function. */
	private int[] partialAssignments;

	/**
	 * A function node for factor graphs.
	 *
	 * @param nodeIdx The index of a node.
	 * @param scope Indexes of agents whose variables are arguments of this function.
	 */
	public FunctionNode(int nodeIdx, List<Integer> scopeList) {
		this.nodeIdx = nodeIdx;
		scope = Utils.list2Array(scopeList);

		R = new Float[scopeList.size()][2];
		partialAssignments = new int[scopeList.size()];

		scopeMap = new HashMap<>();
		int i = 0;
		for (Integer agentIdx : scopeList) {
			R[i][0] = 0f;
			R[i][1] = 0f;
			scopeMap.put(agentIdx, i++);
		}
	}

	public float getR(int agentIdx, int d) {
		int fastMaxSumIdx = 0;

		if (d == nodeIdx)
			fastMaxSumIdx = 1;

		if (scopeMap.get(agentIdx) != null)
			return R[scopeMap.get(agentIdx)][fastMaxSumIdx];

		if (Solver.DEBUG)
			System.err.println(String.format("Function node %d does not have the R-row of agent %d", nodeIdx, agentIdx));

		return 0f;
	}

	public void setPartialAssignment(int agentIdx, int x) {
		partialAssignments[scopeMap.get(agentIdx)] = x;
	}

	/**
	 * Check if a combination of value assignment is consistent with the
	 * partial assignments.
	 *
	 */
	private boolean isConsistent(int[] combination, Integer agentIdx) {
		int a = scopeMap.get(agentIdx);

		for (int i = 0; i < combination.length; i++)
			if (i != a && combination[i] != partialAssignments[i])
				return false;

		return true;
	}

	/**
	 * Check if this combination of values contains a coalition allocation to nodeIdx
	 * that satisfies the temporal constraints.
	 *
	 * @param solver The solver being used.
	 * @param combination Value assignments to all variable in the scope of this function.
	 * @return A float array, where the first element is the number of NCCCs,
	 *         while the second is the time at which l visits nodeIdx if it
	 *         exists, and +infty otherwise.
	 */
	private Float[] evaluateTemporalConstraints(Solver solver, int[] combination) {
		ArrayList<Integer> coalitionList = new ArrayList<>();
		int i;

		/* 1. Get potential coalition (i.e., which agentIdxs want to visit nodeIdx). */
		for (i = 0; i < combination.length; i++)
			if (combination[i] == 1)
				coalitionList.add(scope[i]);

		if (coalitionList.isEmpty())
			return new Float[] {Float.valueOf(0f), Float.valueOf(0f)}; // nodeIdx is 'indifferent' to l

		/* 2. Get agent arrival times to nodeIdx. */
		int[] coalition = Utils.list2Array(coalitionList);
		int[] arrivalTimes = new int[coalition.length];
		Integer[] indexes = new Integer[arrivalTimes.length];

		Location nodeLocation = solver.getNodeLocation(nodeIdx);
		Location agentLocation;
		for (i = 0; i < arrivalTimes.length; i++) {
			agentLocation = solver.getCurrentAgentLocation(coalition[i]);
			arrivalTimes[i] = solver.getCurrentTime() + agentLocation.getTravelTimeTo(nodeLocation, solver.getAgentSpeed(coalition[i]));
			indexes[i] = i;
		}

		/* 3. Sort agents by arrival time to nodeIdx. */
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

		/* 4. Satisfy temporal constraints. */
		float workload = solver.getWorkload(nodeIdx);
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
				interval = solver.getDeadline(nodeIdx) - arrivalTimes[i];

			cValue = solver.getCoalitionValue(nodeIdx, subCoalition);
			subCoalitionWorkloadDone = interval * cValue;

			if (workloadDone + subCoalitionWorkloadDone >= workload) // return minimum t
				return new Float[] {Float.valueOf(i), arrivalTimes[i] + (float) Math.ceil((workload - workloadDone) / cValue)};
			else
				workloadDone += subCoalitionWorkloadDone;
		}

		return new Float[] {Float.valueOf(sortedCoalition.length), Float.POSITIVE_INFINITY};
	}

	public int sendRMessageTo(Integer agentIdx, Map<Integer, VariableNode> variableNodeMap, Solver solver, boolean valuePropagation) {
		Float[] ncccSigma;
		Float sigma;
		Integer idx;
		int[] combination;
		int i, NCCCs = 0;

		int a = scopeMap.get(agentIdx), d;
		VariableNode variableNode;

		for (int coalitionSize = 1; coalitionSize <= scope.length; coalitionSize++) {
			for (int[] combinationIndexes : new Combinations(scope.length, coalitionSize)) {
				combination = getBinaryCombination(combinationIndexes);

				if (valuePropagation && !isConsistent(combination, agentIdx))
					break;

				ncccSigma = evaluateTemporalConstraints(solver, combination);
				NCCCs += ncccSigma[0];
				sigma = ncccSigma[1];

				d = -1;
				for (i = 0; i < combination.length; i++) {
					idx = scopeMap.get(scope[i]); // an agent index
					variableNode = variableNodeMap.get(idx);
					if (!idx.equals(agentIdx) && combination[i] == 1 && variableNode != null)
						sigma = Utils.checkedSum(sigma, variableNode.getQ(nodeIdx, nodeIdx));
					if (idx.equals(agentIdx)) { // Fast Max-Sum index
						if (d == nodeIdx)
							d = 1;
						else
							d = 0;
					}
				}

				if (d > -1 && sigma < R[a][d]) // assuming a minimisation problem
					R[a][d] = sigma;

				if (LIMITED)
					break;
			}
		}

		return NCCCs;
	}

	private int[] getBinaryCombination(int[] combinationIndexes) {
		int[] combination = new int[scope.length];

		for (int index : combinationIndexes)
			combination[index] = 1;

		return combination;
	}

}