package solvers;

import java.util.Arrays;
import java.util.Comparator;

import org.apache.commons.lang3.ArrayUtils;

import model.CFSTP;
import model.FeasibleAllocation;
import model.Results;
import toolkit.Utilities;

/**
 * Cluster-based Task Scheduling (CTS) algorithm for solving CFSTPs.
 *
 * @author lcpz
 */
public class CTS extends Solver {

	public static enum TaskStatus {
		/**
		 * A task is allocable when at least one agent can reach it (feasible),
		 * allocated when at least one agent is reaching or working on it, and completed
		 * when its workload is <= 0.
		 */
		NOT_COMPLETED, ALLOCABLE, ALLOCATED, COMPLETED;
	}

	public static enum AgentStatus {
		FREE, REACHING_A_TASK, WORKING_ON_A_TASK;
	}

	public static enum AssignmentStatus {
		/**
		 * {not working on, feasible to work on, reaching, working on, done with} a task
		 */
		NONE, FEASIBLE, REACHING, WORKING_ON, DONE;
	}

	protected TaskStatus[] taskStatus;
	protected AgentStatus[] agentStatus;
	protected AssignmentStatus[][] assignmentStatus; // [agent, task]

	/* how many agents are currently working at each task */
	protected int[] workingAtTask;

	/**
	 * For each agent (rows), it denotes the task it's reaching (column 0), and the
	 * number of time steps left to reach it (column 1).
	 */
	protected int[][] reachingTask;

	/**
	 * A copy of task workloads that we use for keeping track of how much workload
	 * is left per task.
	 */
	protected float[] workloads;

	/* a counter for computing the average travel time after solving */
	protected int numberOfTravels;

	/**
	 * The number of time steps required to complete each task (0 means
	 * uncompleted).
	 */
	protected int[] completionTime;

	protected float maxTaskWorkload;

	public CTS(CFSTP problem) {
		super(problem);

		taskStatus = new TaskStatus[tasks.length];
		agentStatus = new AgentStatus[agents.length];
		assignmentStatus = new AssignmentStatus[agents.length][tasks.length];
		Arrays.fill(taskStatus, TaskStatus.NOT_COMPLETED);
		Arrays.fill(agentStatus, AgentStatus.FREE);

		workingAtTask = new int[tasks.length];
		reachingTask = new int[agents.length][2];

		for (int a : agents) {
			Arrays.fill(assignmentStatus[a], AssignmentStatus.NONE);
			reachingTask[a][0] = -1;
			reachingTask[a][1] = -1;
		}

		workloads = new float[tasks.length];
		for (int v = 0; v < demands.length; v++) {
			workloads[v] = demands[v][1];
			if (workloads[v] > maxTaskWorkload)
				maxTaskWorkload = workloads[v];
		}

		completionTime = new int[tasks.length];
	}

	/**
	 * Given agent a, return the current closest and uncompleted/allocated task v
	 * reachable by a.
	 *
	 * @param a An agent index.
	 *
	 * @return A task index.
	 */
	protected int getTaskAllocableToAgent(int a) {
		int bestTask[] = new int[] { -1, -1 };
		int bestDeadline[] = new int[] { maxTaskDeadline + 1, maxTaskDeadline + 1 };
		int bestArrivalTime[] = new int[] { maxTaskDeadline + 1, maxTaskDeadline + 1 };
		int idx;

		for (int v : tasks)
			if (taskStatus[v] == TaskStatus.NOT_COMPLETED || taskStatus[v] == TaskStatus.ALLOCATED) {
				idx = 0;
				if (taskStatus[v] == TaskStatus.ALLOCATED)
					idx = 1;
				int arrivalTime = currentTime + problem.getAgentTravelTime(a, agentLocations[a], taskLocations[v]);
				if (arrivalTime <= demands[v][0] && demands[v][0] < bestDeadline[idx]
						&& arrivalTime < bestArrivalTime[idx]) {
					bestDeadline[idx] = demands[v][0];
					bestArrivalTime[idx] = arrivalTime;
					bestTask[idx] = v;
				}
			}

		if (bestTask[0] != -1) // prioritise unallocated tasks
			return bestTask[0];

		return bestTask[1];
	}

	/**
	 * Given a task v, get the agents that can reach v at current time, and return
	 * them sorted by arrival time to v.
	 *
	 * @param v The Task.
	 *
	 * @return a feasible allocation (i.e., the above agents plus related useful
	 *         information)
	 */
	private FeasibleAllocation getFeasibleAgentsByArrivalTime(int v) {
		/* get feasible agents */
		int[] feasibleAgents = new int[agents.length];

		int i = 0;

		for (int a : agents)
			if (assignmentStatus[a][v] == AssignmentStatus.FEASIBLE)
				feasibleAgents[i++] = a;

		if (i == 0)
			return null;

		feasibleAgents = ArrayUtils.subarray(feasibleAgents, 0, i);

		/* get arrival times */
		int[] arrivalTimes = new int[feasibleAgents.length];
		Integer[] indexes = new Integer[arrivalTimes.length];

		for (i = 0; i < arrivalTimes.length; i++) {
			arrivalTimes[i] = currentTime + problem.getAgentTravelTime(feasibleAgents[i],
					agentLocations[feasibleAgents[i]], taskLocations[v]);
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

	private int[] getAgentsWorkingAtTask(int v) {
		if (workingAtTask[v] <= 0)
			return ArrayUtils.EMPTY_INT_ARRAY;

		int[] workers = new int[agents.length];
		int i = 0;

		for (int a : agents)
			if (assignmentStatus[a][v] == AssignmentStatus.WORKING_ON)
				workers[i++] = a;

		return ArrayUtils.subarray(workers, 0, i);
	}

	private void allocate(int v, int[] agents, int[] arrivalTimes) {
		for (int i = 0; i < agents.length; i++)
			if (agentStatus[agents[i]] == AgentStatus.FREE) {
				isBusyAgent[agents[i]] = true;
				int travelTimeSteps = arrivalTimes[i] - currentTime + 1;

				if (travelTimeSteps > 0) {
					agentStatus[agents[i]] = AgentStatus.REACHING_A_TASK;
					assignmentStatus[agents[i]][v] = AssignmentStatus.REACHING;
					reachingTask[agents[i]] = new int[] { v, travelTimeSteps };
					avgTravelTime += travelTimeSteps;
					numberOfTravels++;
				} else { /* agent is already at task location */
					agentStatus[agents[i]] = AgentStatus.WORKING_ON_A_TASK;
					assignmentStatus[agents[i]][v] = AssignmentStatus.WORKING_ON;
					workingAtTask[v]++;
				}
			}
	}

	@Override
	public void solve() { /* Total: O(|V||A|^2) */
		int numberOfCompletedTasks = 0;
		StringBuilder s;

		do {
			if (DEBUG)
				System.out.println(String.format("[%3d, %3d]", currentTime, numberOfCompletedTasks));

			for (int a : agents)
				/* if possible, allocate a to a task */
				if (agentStatus[a] == AgentStatus.FREE) {
					int v = getTaskAllocableToAgent(a);
					if (v > -1) {
						assignmentStatus[a][v] = AssignmentStatus.FEASIBLE;
						taskStatus[v] = TaskStatus.ALLOCABLE;
					}
					/* otherwise, if a reached its assigned task, update its status and position */
				} else if (reachingTask[a][0] > -1
						&& assignmentStatus[a][reachingTask[a][0]] == AssignmentStatus.REACHING) {
					if (taskStatus[reachingTask[a][0]] == TaskStatus.COMPLETED) {
						agentStatus[a] = AgentStatus.FREE;
						isBusyAgent[a] = false;
						assignmentStatus[a][reachingTask[a][0]] = AssignmentStatus.NONE;
					} else if (--reachingTask[a][1] <= 0) {
						workingAtTask[reachingTask[a][0]]++;
						agentStatus[a] = AgentStatus.WORKING_ON_A_TASK;
						assignmentStatus[a][reachingTask[a][0]] = AssignmentStatus.WORKING_ON;
						agentLocations[a] = taskLocations[reachingTask[a][0]];
					}
				}

			for (int v : tasks) {
				if (taskStatus[v] == TaskStatus.ALLOCABLE) {
					FeasibleAllocation feasibleAllocation = getFeasibleAgentsByArrivalTime(v);
					int[] feasibleAgents = feasibleAllocation.getAgents();
					int[] agentsWorkingAtTask = getAgentsWorkingAtTask(v);
					int[] arrivalTimes = feasibleAllocation.getArrivalTimes();
					int[] agentsToAssign = null;
					float cValue;
					int i;

					for (i = 0; i < feasibleAgents.length; i++) {
						agentsToAssign = ArrayUtils.subarray(feasibleAgents, 0, i + 1);

						if (agentsWorkingAtTask.length > 0)
							cValue = problem.getCoalitionValue(v,
									ArrayUtils.addAll(agentsWorkingAtTask, agentsToAssign));
						else
							cValue = problem.getCoalitionValue(v, agentsToAssign);

						float workloadDone = 0f;
						for (int j = 0; j < agentsToAssign.length - 1; j++) {
							/*
							 * If multiple agents arrive at the same time, consider only the last one in the
							 * order.
							 */
							if (arrivalTimes[j] == arrivalTimes[j + 1])
								continue;

							workloadDone += (arrivalTimes[j + 1] - arrivalTimes[j])
									* problem.getCoalitionValue(v, ArrayUtils.addAll(
											ArrayUtils.subarray(agentsToAssign, 0, j + 1), agentsWorkingAtTask));
						}

						/* if coalition of first i agents can complete v within deadline */
						if (cValue * (demands[v][0] - arrivalTimes[i]) >= workloads[v] - workloadDone)
							break;
					}

					while (++i < feasibleAgents.length)
						assignmentStatus[feasibleAgents[i]][v] = AssignmentStatus.NONE;

					allocate(v, agentsToAssign, arrivalTimes);

					taskStatus[v] = TaskStatus.ALLOCATED;
				}

				if (taskStatus[v] == TaskStatus.ALLOCATED) {
					if (DEBUG)
						s = new StringBuilder(String.format("%5d (%.2f) -> ", v, workloads[v]));

					int[] workers = new int[workingAtTask[v]];
					int[] reachers = new int[agents.length];

					int i = 0, j = 0;

					for (int a : agents)
						switch (assignmentStatus[a][v]) {
						case WORKING_ON:
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
							s.append(String.format("R%s ", Arrays.toString(reachers)));
					}

					if (i > 0) {
						if (DEBUG)
							s.append(String.format("W%s ", Arrays.toString(workers)));

						/* reduce w_v by u(C) */
						workloads[v] -= problem.getCoalitionValue(v, workers);
						completionTime[v]++;

						if (workloads[v] <= 0) {
							if (DEBUG)
								s.append(" \u2713");

							workingAtTask[v] = 0;
							taskStatus[v] = TaskStatus.COMPLETED;
							numberOfCompletedTasks++;
							for (int a : workers) {
								assignmentStatus[a][v] = AssignmentStatus.DONE;
								agentStatus[a] = AgentStatus.FREE;
								isBusyAgent[a] = false;
							}
						}
					}

					if (DEBUG)
						System.out.println(s);
				}
			}

			if (numberOfCompletedTasks < tasks.length && allAgentsAreAvailable()) {
				if (DEBUG)
					System.out.println(String.format(
							"\nNo tasks can be further allocated, stopping before max deadline (%d)", maxTaskDeadline));
				break;
			}

			currentTime++;
		} while (!allAgentsAreAvailable() && numberOfCompletedTasks < tasks.length && currentTime <= maxTaskDeadline);

		if (numberOfTravels > 0)
			avgTravelTime /= numberOfTravels;

		if (numberOfCompletedTasks > 0)
			avgCompletionTime = Utilities.sum(completionTime) / (float) numberOfCompletedTasks;

		results = new Results(avgTravelTime, avgCompletionTime, numberOfCompletedTasks / (float) tasks.length);

	}

}
