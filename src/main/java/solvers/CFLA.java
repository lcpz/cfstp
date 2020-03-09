package solvers;

import java.util.Arrays;
import java.util.Comparator;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.util.Combinations;

import model.Allocation;
import model.CFSTP;
import model.Results;
import toolkit.Mathematics;
import toolkit.Utilities;

/**
 * Coalition Formation with improved Look-Ahead (CFLA^+) algorithm for solving CFSTPs.
 *
 * @author lcpz
 */
public class CFLA extends Solver {

	/*
	 * The set of all possible allocations of agents to tasks:
	 * [time][agentId][taskId]
	 */
	protected boolean[][][] T;

	/* CFLA or CFLA^+ */
	private boolean improved;

	public CFLA(CFSTP problem) {
		super(problem);
		/* setting T */
		T = new boolean[problem.getMaxTaskDeadline()][agents.length][tasks.length];
	}

	public CFLA(CFSTP problem, boolean improved) {
		this(problem);
		this.improved = improved;
	}

	/**
	 * Get the number of time steps required to reach a task, by each agent in a
	 * given coalition.
	 *
	 * @param task      The task to reach.
	 * @param coalition The coalition.
	 *
	 * @return An array of integers, with index i defining the time steps needed by
	 *         agent i of <code>coalition</code> to reach <code>task</code>.
	 */
	public int[] getCoalitionTravelTimes(int task, int[] coalition) {
		int[] travelTimes = new int[coalition.length];

		for (int a = 0; a < coalition.length; a++)
			travelTimes[a] = problem.getAgentTravelTime(agents[coalition[a]], agentLocations[coalition[a]], taskLocations[task]);

		return travelTimes;
	}

	private float getSubCoalitionsContribution(int task, int[] coalition, int[] travelTimes) {
		int i, contribution = 0;

		/* get arrival times */
		ArrayUtils.addAll(travelTimes, currentTime);

		/* sort coalition agents by arrival times */
		Integer[] sorted = Utilities.getRangeArray(coalition.length);
		Arrays.sort(sorted, new Comparator<Integer>() {
			public int compare(Integer o1, Integer o2) {
				if (travelTimes[o1] < travelTimes[o2])
					return -1;
				if (travelTimes[o1] == travelTimes[o2])
					return 0;
				return 1;
			}
		});

		int[] sortedAgents = ArrayUtils.toPrimitive(sorted);

		/* get subcoalitions contribution */
		for (i = 0; i < sortedAgents.length - 1; i++)
			contribution += problem.getCoalitionValue(task, ArrayUtils.subarray(sortedAgents, 0, i + 1))
			                * (travelTimes[i+1] - travelTimes[i]);

		return contribution;
	}

	// Step 1: for each task v, define which agents can reach v at current time
	protected void setFeasibleAgentAllocationsToAllTasks() {
		int a, v;
		for (a = 0; a < agents.length; a++)
			if (!isBusyAgent[a]) /* given each free agent a */
				for (v = 0; v < tasks.length; v++)
					if (!isAllocatedTask[v]) /* given each uncompleted task v */
						/* if a can reach v within deadline */
						if (currentTime +
							problem.getAgentTravelTime(agents[a], agentLocations[a], taskLocations[v]) <= demands[v][0])
							T[currentTime][a][v] = true;
	}

	// Step 2: choosing the best coalition for task v at current time
	protected Allocation ECF(int v) {
		int a, n = agents.length, f = 0;

		/* current feasible agent allocations to task v */
		int[] feasibleAgents = new int[n];
		for (a = 0; a < n; a++)
			if (T[currentTime][a][v])
				feasibleAgents[f++] = agents[a];

		if (f < n)
			feasibleAgents = ArrayUtils.subarray(feasibleAgents, 0, f);

		/* the Earliest-Completion-First (ECF) coalition */
		int[] bestCoalition = ArrayUtils.EMPTY_INT_ARRAY;

		/* the travel time of bestCoalition */
		int bestTravelTime = -1;

		/* minimum completion time of v, initialised to its deadline */
		int bestCompletionTime = demands[v][0] + 1;

		/*
		 * this upper bound, paired with the following while loop logic, ensures that,
		 * in the worst case, task v can only be completed by the grand coalition (i.e.,
		 * the set of all agents)
		 */
		int minCoalitionSize = f + 1;

		a = 0;
		while (++a <= f && minCoalitionSize == f + 1)
			/*
			 * huge limit: coalition size is typical 1, hence coalitions of higher value (if
			 * available) are discarded
			 */
			for (int[] coalition : new Combinations(f, a) ) {
				/* replace indexes with actual feasible agent IDs in coalition */
				coalition = Utilities.subarray(feasibleAgents, coalition);

				int[] cTravelTimes = getCoalitionTravelTimes(v, coalition);
				int maxTravelTime = Utilities.getMax(cTravelTimes);

				/*
				 * the coalition does not start working until all its agent have reached the task location,
				 * hence we remove the workload done by all earlier subcoalitions
				 */

				int tInterval = demands[v][0] - currentTime - maxTravelTime + 1;

				if (tInterval <= 0)
					continue; /* coalition can't reach task v */

				float cValue = problem.getCoalitionValue(v, coalition);
				float workloadDone = getSubCoalitionsContribution(v, coalition, cTravelTimes);

				/* if full coalition can complete v by its deadline, starting from currentTime */
				if (tInterval * cValue >= demands[v][1] - workloadDone) {
					/* then a is the minimum size of the coalition that can complete v */
					minCoalitionSize = a;

					/*
					 * get the minimum time tminmax at which this coalition can complete v, that is,
					 * the minimum number of time step t between currentTime and deadline_v s.t.:
					 * t * u(C) >= workload_v
					 */
					int tminmax = 0;
					while (++tminmax * cValue < demands[v][1] - workloadDone && tminmax <= tInterval);
					tminmax += currentTime;

					if (tminmax < bestCompletionTime) {
						bestCoalition = coalition;
						bestTravelTime = maxTravelTime;
						bestCompletionTime = tminmax;
					}
				}

			}

		return new Allocation(v, bestCoalition, bestTravelTime, bestCompletionTime);
	}

	/**
	 * 1-step look-ahead procedure used by {@link solvers.CFLA#solve()}.
	 *
	 * It computes the degree of v, that is, the number of tasks that can be
	 * allocated (and, hence, completed) after v is completed by its ECF coalition.
	 *
	 * @param allocation  An ECF coalition allocation to a task.
	 * @param isFreeAgent The agents that are free at
	 *                    <code>allocation.getCompletionTime() + 1</code>.
	 *
	 * @return v's degree
	 */
	protected int lookAhead(Allocation allocation, boolean[] isFreeAgent) {
		int v = allocation.getTask();
		int[] coalition = allocation.getAgents();
		int vCompletionTime = allocation.getCompletionTime();
		int degreeV = 0;

		for (int v2 = 0; v2 < tasks.length; v2++)
			/* if v2 is not allocated and it's not v */
			if (!isAllocatedTask[v2] && v != v2) {

				/* Improvement: AND d_v2 >= d_v */
				if (improved && demands[v2][0] < demands[v][0])
					continue;

				/*
				 * if agents in coalition + free agents at completionTime+1 can reach v2 and
				 * form a coalition that can complete v2, then increase degreeV by 1
				 */

				/* 1. get the agents that are free at completionTime */
				int f = 0;
				for (boolean b : isFreeAgent) if (b) f++;
				int[] feasibleAgents = new int[f];
				int i, j = 0;
				for (i = 0; i < isFreeAgent.length; i++)
					if (isFreeAgent[i])
						feasibleAgents[j++] = agents[i];
				feasibleAgents = ArrayUtils.addAll(feasibleAgents, coalition);

				/* 2. select those that can reach v2 within deadline */
				for (i = 0; i < feasibleAgents.length; i++)
					if (vCompletionTime
						+ problem.getAgentTravelTime(feasibleAgents[i], agentLocations[feasibleAgents[i]], taskLocations[v2]) > demands[v2][0])
						feasibleAgents[i] = -1;
				feasibleAgents = ArrayUtils.removeAllOccurences(feasibleAgents, -1);

				/* 3. check if it exists an ECF coalition that can complete v2 */
				i = 0;
				while (++i <= feasibleAgents.length)
					/*
					 * huge limit: coalition size is typical 1, hence coalitions of higher value (if
					 * available) are discarded
					 */
					for (int[] c : new Combinations(feasibleAgents.length, i)) {
						/* replace indexes with actual feasible agent IDs in coalition */
						c = Utilities.subarray(feasibleAgents, c);
						int[] cTravelTimes = getCoalitionTravelTimes(v, c);
						int maxTravelTime = Utilities.getMax(cTravelTimes);

						int tInterval = demands[v][0] - vCompletionTime - maxTravelTime + 1;

						if (tInterval <= 0)
							continue; /* coalition c can't reach task v */

						/* if c can complete v2 by its deadline */
						if (tInterval * problem.getCoalitionValue(v2, c) >=
							demands[v2][1] - getSubCoalitionsContribution(v2, c, cTravelTimes)) {
							if (!improved)
								/* CFLA: each task that can be completed after v
								 * has the same weight (1) */
								degreeV++;
							else
								/* CFLA^+: each task that can be completed after v
								 * weights inversely proportional to its workload */
								degreeV += 2 - Mathematics.getZ(demands[v2][1], minTaskWorkload, maxTaskWorkload);
								/* in other words, the higher degreeV is, the more
								 * tasks with light workload we can complete after v; hence,
								 * by completing first the tasks with light workload,
								 * hypothetically we can complete more tasks in general
								 */

							i = feasibleAgents.length; // break external while as well
							break;
						}
					}
			}

		return degreeV;
	}

	// Step 3: allocate task with with 1-step look-ahead (CFLA core)
	public void solve() {
		/* we assume that initial number of completed tasks is zero */
		int numberOfAllocatedTasks = 0;

		/* time steps at which agents are set 'free' */
		boolean[][] freeAt = new boolean[maxTaskDeadline+1][agents.length];

		do {
			int maxTaskDegree = 0; // current max task degree
			Allocation nextAllocation = new Allocation(); // the next allocation

			/* set 'free' the agents that completed task in the previous time step */
			for (int a : agents)
				if (freeAt[currentTime][a])
					isBusyAgent[a] = false;

			/* define feasible allocations */
			setFeasibleAgentAllocationsToAllTasks();

			/* limit: we visit all tasks, and do just 1 assignment */
			for (int v = 0; v < tasks.length; v++)
				if (!isAllocatedTask[v]) {
					/* get ECF coalition allocation to task v */
					Allocation ecf = ECF(v);

					int[] coalition = ecf.getAgents();

					/* if no ECF coalition can be allocated to v at currentTime */
					if (coalition == ArrayUtils.EMPTY_INT_ARRAY)
						continue; /* go to next unallocated task */

					int completionTime = ecf.getCompletionTime();

					/* 1-step look-ahead phase */
					int degreeV = -1; // do not allocate tasks with too high workload
					if (completionTime <= maxTaskDeadline)
						degreeV = lookAhead(ecf, freeAt[completionTime + 1]);

					if (degreeV > maxTaskDegree) {
						maxTaskDegree = degreeV;
						nextAllocation = ecf;
					}
				}

			int nextTask = nextAllocation.getTask();

			/* if it exists, allocate nextCoalition to nextTask */
			if (nextTask > -1) {
				int timeToSetFree = nextAllocation.getCompletionTime() + 1;

				for (int agent : nextAllocation.getAgents()) {
					isBusyAgent[agent] = true;
					if (timeToSetFree <= maxTaskDeadline)
						freeAt[timeToSetFree][agent] = true;
					agentLocations[agent] = taskLocations[nextTask];
				}

				isAllocatedTask[nextTask] = true;
				numberOfAllocatedTasks++;
				avgTravelTime += nextAllocation.getTravelTime();
				avgCompletionTime += nextAllocation.getCompletionTime() - currentTime;
			}

			/*
			 * if all agents are currently available, then it is no longer possible to
			 * allocate tasks, hence stop earlier
			 */
			if (allAgentsAreAvailable())
				break;

			if (DEBUG)
				printCurrentAllocation(nextAllocation, numberOfAllocatedTasks);

			/* go to next time step */
			currentTime++;
		} while (numberOfAllocatedTasks < tasks.length && currentTime < maxTaskDeadline);

		if (numberOfAllocatedTasks > 0) {
			avgTravelTime /= numberOfAllocatedTasks;
			avgCompletionTime /= numberOfAllocatedTasks;
		}

		results = new Results(avgTravelTime, avgCompletionTime, numberOfAllocatedTasks / (float) tasks.length);
	}

	/**
	 * Print the percentage of allocated tasks and allocation at current time.
	 *
	 * @param allocation             An allocation set at current time.
	 * @param numberOfAllocatedTasks The current number of allocated tasks.
	 */
	protected void printCurrentAllocation(Allocation allocation, int numberOfAllocatedTasks) {
		float perc = numberOfAllocatedTasks / (float) tasks.length * 100;
		System.out.println(String.format("[%3d, %.2f] %s", currentTime, perc, allocation));
	}

}