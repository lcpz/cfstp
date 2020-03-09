package solvers;

import model.CFSTP;
import model.Results;

/**
 * An algorithm for solving CFSTPs.
 *
 * @author lcpz
 */
public abstract class Solver {

	public static final boolean DEBUG = true;

	/* The problem */
	protected CFSTP problem;

	/* Data structures retrieved from problem */
	protected int[] tasks, agents;
	protected int[][] taskLocations, agentLocations, demands;
	protected int maxTaskDeadline, minTaskDeadline, maxTaskWorkload, minTaskWorkload;

	/* Final results */
	protected Results results;

	/* Current system time, starting at 0 */
	protected int currentTime;

	/* Average travel and completion time */
	protected float avgTravelTime, avgCompletionTime;

	/*
	 * Flags to determine which agents are currently busy, and which tasks are
	 * currently allocated. A task that is allocated at (the beginning of) time step
	 * t can be either be completed by the end of t, or within its deadline. An
	 * agent is busy when either is reaching or working at a task it is allocated
	 * to.
	 */
	protected boolean[] isBusyAgent, isAllocatedTask;

	public Solver(CFSTP problem) {
		this.problem = problem;
		tasks = problem.getTasks();
		agents = problem.getAgents();
		taskLocations = problem.getTaskLocations();
		agentLocations = problem.getInitialAgentLocations();
		demands = problem.getDemands();
		maxTaskDeadline = problem.getMaxTaskDeadline();
		minTaskDeadline = problem.getMinTaskDeadline();
		maxTaskWorkload = problem.getMaxTaskWorkload();
		minTaskWorkload = problem.getMinTaskWorkload();
		isBusyAgent = new boolean[agents.length];
		isAllocatedTask = new boolean[tasks.length];
	}

	/**
	 * Check if all agents are currently not busy in a coalition.
	 *
	 * @return A boolean.
	 */
	protected boolean allAgentsAreAvailable() {
		for (boolean a : isBusyAgent)
			if (a)
				return false;
		return true;
	}

	public abstract void solve();

	public int getCurrentTime() {
		return currentTime;
	}

	public Results getResults() {
		return results;
	}

}