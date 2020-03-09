package model;

import java.util.Arrays;

import org.apache.commons.lang3.ArrayUtils;

/**
 * Data structure containing information about a task-coalition allocation.
 *
 * @author lcpz
 */
public class Allocation {

	protected int task;
	protected int[] agents;
	private int travelTime;
	private int completionTime;

	/**
	 * @param task           The task to which the coalition is allocated.
	 * @param agents         The set of agents composing the coalition.
	 * @param travelTime     The travel time units required by the coalition to
	 *                       reach the task.
	 * @param completionTime The completion time units required by the coalition to
	 *                       complete the task.
	 */
	public Allocation(int task, int[] agents, int travelTime, int completionTime) {
		this.task = task;
		this.agents = agents;
		if (agents == null)
			agents = ArrayUtils.EMPTY_INT_ARRAY;
		this.travelTime = travelTime;
		this.completionTime = completionTime;
	}

	/**
	 * Return an empty allocation.
	 */
	public Allocation() {
		task = -1;
		agents = ArrayUtils.EMPTY_INT_ARRAY;
		travelTime = -1;
		completionTime = -1;
	}

	@Override
	public String toString() {
		return String.format("%3d -> %s [%3d, %3d]", task, Arrays.toString(agents), travelTime, completionTime);
	}

	public int getTask() {
		return task;
	}

	public int[] getAgents() {
		return agents;
	}

	public int getTravelTime() {
		return travelTime;
	}

	public int getCompletionTime() {
		return completionTime;
	}

}