package model;

import exceptions.NegativeDeadlineException;
import exceptions.NotPositiveWorkloadException;

/**
 * A Coalition Formation with Spatial and Temporal constraints Problem (CFSTP).
 *
 * @author lcpz
 */
public abstract class CFSTP {

	/* Tasks and agents are uniquely identified by integers */
	protected int[] tasks, agents;

	/* Task locations (static) are 2-dimensional points: [taskId][x,y] */
	protected int[][] taskLocations;

	/* Likewise, initial agent locations are 2-dimensional points: [agentId][x,y] */
	protected int[][] initialAgentLocations;

	/*
	 * Task demands: [deadline, workload].
	 *
	 * Row of index <code>i</code> contains the demands of <code>tasks[i]</code>.
	 */
	protected int[][] demands;

	/* Some useful data */
	protected int maxTaskDeadline, minTaskDeadline, maxTaskWorkload, minTaskWorkload;

	public CFSTP(int[] agents, int[] tasks, int[][] initialAgentLocations, int[][] taskLocations, int[][] demands) {
		this.agents = agents;
		this.tasks = tasks;
		this.taskLocations = taskLocations;
		this.demands = demands;

		minTaskDeadline = Integer.MAX_VALUE;
		minTaskWorkload = Integer.MAX_VALUE;

		try {
			for (int i = 0; i < demands.length; i++) {
				if (demands[i][0] < 0)
					throw new NegativeDeadlineException(String.format("task %d has deadline = %d", i, demands[i][0]));
				if (demands[i][1] <= 0)
					throw new NotPositiveWorkloadException(String.format("task %d has workload = %d", i, demands[i][1]));

				if (demands[i][0] > maxTaskDeadline)
					maxTaskDeadline = demands[i][0];
				if (demands[i][0] < minTaskDeadline)
					minTaskDeadline = demands[i][0];

				if (demands[i][1] > maxTaskWorkload)
					maxTaskWorkload = demands[i][1];
				if (demands[i][1] < minTaskWorkload)
					minTaskWorkload = demands[i][1];

			}
		} catch (NegativeDeadlineException | NotPositiveWorkloadException e) {
			e.printStackTrace();
		}

		/* setting initial agent locations */
		this.initialAgentLocations = initialAgentLocations;
	}

	/**
	 * The time taken for an agent to travel from one location to another.
	 *
	 * This is function $\rho$ in section 2.1 of (Ramchurn et al., 2010).
	 *
	 * @param agentId       The agent's identifier; to be used when you assume that
	 *                      agents cannot move at the same speed.
	 * @param agentLocation The agent's current location, in (x, y) coordinates.
	 * @param taskLocation  The location of a given task, in (x, y) coordinates.
	 *
	 * @return The time steps required by agentId to travel from agentLocation to
	 *         taskLocation.
	 */
	public abstract int getAgentTravelTime(int agentId, int[] agentLocation, int[] taskLocation);

	/**
	 * Given task $v$ and coalition $C \in 2^A$, the coalition value of $C$ $u(C)$
	 * determines the amount of workload that $C$ does in a time step.
	 *
	 * In the CFSTP model, it is possible to take $C$ so that $u(C) = workload_v$,
	 * and so complete a task v in only one time step.
	 *
	 * @param task      The task <code>v</code>.
	 * @param coalition A coalition assigned to <code>v</code>.
	 *
	 * @return The value of <code>coalition</code.
	 */
	public abstract float getCoalitionValue(int task, int[] coalition);

	public int[] getTasks() {
		return tasks;
	}

	public int[] getAgents() {
		return agents;
	}

	public int[][] getTaskLocations() {
		return taskLocations;
	}

	public int[][] getInitialAgentLocations() {
		return initialAgentLocations;
	}

	public int[][] getDemands() {
		return demands;
	}

	public int getMaxTaskDeadline() {
		return maxTaskDeadline;
	}

	public int getMinTaskDeadline() {
		return minTaskDeadline;
	}

	public int getMaxTaskWorkload() {
		return maxTaskWorkload;
	}

	public int getMinTaskWorkload() {
		return minTaskWorkload;
	}

}