package solvers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import org.apache.commons.lang3.ArrayUtils;

import locations.Location;
import model.CFSTP;
import model.Results;

/**
 * An algorithm for solving CFSTPs.
 *
 * @author lcpz
 */
public abstract class Solver {

	public static final boolean DEBUG = false;

	/* The problem */
	protected CFSTP problem;

	/* Data structures retrieved from problem */
	protected int[] nodes, agents;
	protected int[][] demands;
	protected Location[] nodeLocations, agentLocations;
	protected int maximumProblemCompletionTime;

	/* Current system time, starting at 0 */
	protected int currentTime;

	/**
	 * Flags to determine which agents are currently busy, and which nodes are
	 * currently allocated. A node that is allocated at (the beginning of) time step
	 * t can be either be completed by the end of t, or within its deadline. An
	 * agent is busy when either is reaching or working at a node it is allocated
	 * to.
	 */
	protected boolean[] isBusyAgent, isAllocatedNode;

	/* DCOP metrics. */
	protected int messagesSent;
	protected int NCCCs;
	protected float networkLoad;
	//protected List<Integer> coalitionSizes; // size of the coalition of each coalition allocation

	protected Results results;

	public static enum NodeStatus {
		/**
		 * A node is allocable when at least one agent can reach it (feasible),
		 * allocated when at least one agent is reaching or visiting it, and completed
		 * when its workload is <= 0.
		 */
		NOT_COMPLETED, ALLOCABLE, ALLOCATED, COMPLETED;
	}

	public static enum AgentStatus {
		FREE, REACHING_A_NODE, VISITING_A_NODE;
	}

	public static enum AssignmentStatus {
		/**
		 * {not visiting, feasible to work on, reaching, visiting, done with} a node
		 */
		NONE, FEASIBLE, REACHING, VISITING, DONE;
	}

	protected NodeStatus[] nodeStatus;
	protected AgentStatus[] agentStatus;
	protected AssignmentStatus[][] assignmentStatus; // [agent, node]

	/* how many agents are currently visiting each node */
	protected int[] visitingNode;

	/**
	 * For each agent (rows), it denotes the node it's reaching (column 0), and the
	 * number of time steps left to reach it (column 1).
	 */
	protected int[][] reachingNode;

	/**
	 * A copy of node workloads that we use for keeping track of how much workload
	 * is left per node.
	 */
	protected float[] workloads;

	public boolean stoppingCondition;

	public Solver(CFSTP problem) {
		this.problem = problem;
		nodes = problem.nodes;
		agents = problem.agents;
		nodeLocations = problem.nodeLocations;
		agentLocations = problem.initialAgentLocations;
		demands = problem.demands;
		maximumProblemCompletionTime = problem.maximumProblemCompletionTime;
		isBusyAgent = new boolean[agents.length];
		isAllocatedNode = new boolean[nodes.length];

		nodeStatus = new NodeStatus[nodes.length];
		agentStatus = new AgentStatus[agents.length];
		assignmentStatus = new AssignmentStatus[agents.length][nodes.length];
		Arrays.fill(nodeStatus, NodeStatus.NOT_COMPLETED);
		Arrays.fill(agentStatus, AgentStatus.FREE);

		visitingNode = new int[nodes.length];
		reachingNode = new int[agents.length][2];

		int i;
		for (i = 0; i < agents.length; i++) {
			Arrays.fill(assignmentStatus[i], AssignmentStatus.NONE);
			reachingNode[i][0] = -1;
			reachingNode[i][1] = -1;
		}

		workloads = new float[demands.length];
		for (i = 0; i < demands.length; i++)
			workloads[i] = demands[i][1];
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

	/**
	 * Solve a CFSTP snapshot at time t.
	 *
	 * @return n The number of nodes whose visit is completed at t.
	 */
	public abstract int solve(int t);

	public void solve() {
		int visitedNodesTotal = 0;
		messagesSent = 0;
		networkLoad = 0;
		NCCCs = 0;
		//coalitionSizes = new ArrayList<>(nodes.length);

		do {
			if (problem.dynamismType > 0)
				problem.setTime(currentTime);

			visitedNodesTotal += solve(currentTime);

			if (DEBUG) {
				if (problem.dynamismType == 0)
					System.out.println(String.format("[time: %3d, visited: %s]", currentTime, 100 * visitedNodesTotal/(float) nodes.length));
				else
					System.out.println(String.format("[time: %3d, agents: %d, nodes: %d, visited: %s]", currentTime, problem.getCurrentNumberOfAgents(), problem.getCurrentNumberOfNodes(), 100 * visitedNodesTotal/(float) nodes.length));
			}

			if (problem.dynamismType == 0 && visitedNodesTotal < nodes.length && stoppingCondition) {
				if (DEBUG)
					System.out.println(String.format(
					"\nNo nodes can be further allocated, stopping before maximum problem completion time (%d)",
					maximumProblemCompletionTime));

				break;
			}

		} while (visitedNodesTotal < nodes.length && ++currentTime <= maximumProblemCompletionTime);

		float[] values = new float[] {
			(float) messagesSent,
			networkLoad, // size of messages sent
			(float) NCCCs,
			//(float) currentTime, // problem completion time (useful only in static environments)
			//(float) new Median().evaluate(Utils.intList2DoubleArray(coalitionSizes)),
			100 * (visitedNodesTotal / (float) nodes.length) // percentage of visited nodes
		};

		results = new Results(values);
	}

	protected void allocate(int v, int[] coalition, int[] arrivalTimes) {
		if (coalition.length == 0)
			return;

		for (int i = 0; i < coalition.length; i++)
			if (agentStatus[coalition[i]] == AgentStatus.FREE) {
				isBusyAgent[coalition[i]] = true;
				int travelTimeSteps = arrivalTimes[i] - currentTime + 1;

				if (travelTimeSteps > 0) {
					agentStatus[coalition[i]] = AgentStatus.REACHING_A_NODE;
					assignmentStatus[coalition[i]][v] = AssignmentStatus.REACHING;
					reachingNode[coalition[i]] = new int[] { v, travelTimeSteps };
				} else { /* agent is already at node location */
					agentStatus[coalition[i]] = AgentStatus.VISITING_A_NODE;
					assignmentStatus[coalition[i]][v] = AssignmentStatus.VISITING;
					visitingNode[v]++;
				}
			}

		if (DEBUG)
			System.out.println(String.format("allocating %d to %s", v, Arrays.toString(coalition)));

		nodeStatus[nodes[v]] = NodeStatus.ALLOCATED;

		//coalitionSizes.add(Integer.valueOf(coalition.length)); // does not work with CTS
	}

	protected void updateAgentStatus(int a) {
		if (nodeStatus[reachingNode[a][0]] == NodeStatus.COMPLETED) {
			agentStatus[a] = AgentStatus.FREE;
			isBusyAgent[a] = false;
			assignmentStatus[a][reachingNode[a][0]] = AssignmentStatus.NONE;
		} else if (--reachingNode[a][1] <= 0) {
			visitingNode[reachingNode[a][0]]++;
			agentStatus[a] = AgentStatus.VISITING_A_NODE;
			assignmentStatus[a][reachingNode[a][0]] = AssignmentStatus.VISITING;
			agentLocations[a] = nodeLocations[reachingNode[a][0]];
		}
	}

	/**
	 * Updates the status of each node and checks how many nodes have been
	 * visited at <code>currentTime</code>.
	 *
	 * @return An integer.
	 */
	protected int updateNodeStatus() {
		int visitedNodesTotalNow = 0;
		int[] workers, reachers;
		int i, j, a;
		StringBuilder s;

		for (int v = 0; v < nodes.length; v++)
			if (nodeStatus[v] == NodeStatus.ALLOCATED) {
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

						visitingNode[v] = 0;
						nodeStatus[v] = NodeStatus.COMPLETED;
						visitedNodesTotalNow++;
						for (a = 0; a < workers.length; a++) {
							assignmentStatus[a][v] = AssignmentStatus.DONE;
							agentStatus[a] = AgentStatus.FREE;
							isBusyAgent[a] = false;
						}
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

		return visitedNodesTotalNow;
	}

	protected void removeAgent(int a) {
		if (DEBUG)
			System.out.println("Removing agent " + agents[a]);
		if (agentStatus[agents[a]] == AgentStatus.VISITING_A_NODE)
			visitingNode[reachingNode[agents[a]][0]]--;
		agentStatus[agents[a]] = AgentStatus.FREE;
		assignmentStatus[agents[a]][reachingNode[agents[a]][0]] = AssignmentStatus.NONE;
		reachingNode[agents[a]][0] = -1;
	}


	/**
	 * Get arrival times of given coalition to given node.
	 *
	 * @param coalition A set of agent indexes.
	 * @param nodeIdx A node index.
	 * @return An array of integers.
	 */
	protected int[] getArrivalTimes(int[] coalition, int nodeIdx) {
		int[] arrivalTimes = new int[coalition.length];
		Location agentLocation, nodeLocation = nodeLocations[nodeIdx];

		for (int i = 0; i < arrivalTimes.length; i++) {
			agentLocation = agentLocations[coalition[i]];
			arrivalTimes[i] = currentTime + agentLocation.getTravelTimeTo(nodeLocation, problem.getAgentSpeed(coalition[i]));
		}

		return arrivalTimes;
	}

	/**
	 * Given free agent a, return the current closest and uncompleted/allocated
	 * node v reachable by a.
	 *
	 * @param a An agent index.
	 * @return An ArrayList of node indexes.
	 */
	protected ArrayList<Integer> getFreeAgentDomain(int a) {
		if (!agentStatus[a].equals(AgentStatus.FREE)) {
			if (DEBUG)
				System.err.println(String.format("Agent %d is not free at time %d", a, currentTime));
			return new ArrayList<>();
		}

		ArrayList<Integer> agentDomain = new ArrayList<>();

		for (int v = 0; v < nodes.length; v++)
			if (problem.dynamismType < 2 || (problem.dynamismType == 2 && problem.enabled[v]))
				if (nodeStatus[v] == NodeStatus.NOT_COMPLETED || nodeStatus[v] == NodeStatus.ALLOCATED)
					/* If the agent can arrive to v by its deadline, add v to the agent domain. */
					if (currentTime + agentLocations[a].getTravelTimeTo(nodeLocations[v], problem.getAgentSpeed(a)) < demands[v][0])
						agentDomain.add(Integer.valueOf(v));

		/* sort agent domain by arrival times */
		agentDomain.sort(new Comparator<>() {
			public int compare(Integer nodeIdx1, Integer nodeIdx2) {
				int arrivalTime1 = currentTime + agentLocations[a].getTravelTimeTo(nodeLocations[nodeIdx1], problem.getAgentSpeed(a));
				int arrivalTime2 = currentTime + agentLocations[a].getTravelTimeTo(nodeLocations[nodeIdx2], problem.getAgentSpeed(a));
				return arrivalTime1 - arrivalTime2;
			}
		});

		if (DEBUG)
			System.out.println(String.format("Agent %d can reach nodes %s", a, agentDomain.toString()));

		return agentDomain;
	}

	public int getCurrentTime() {
		return currentTime;
	}

	public Location getCurrentAgentLocation(int a) {
		return agentLocations[a];
	}

	public Location getNodeLocation(int v) {
		return nodeLocations[v];
	}

	public float getAgentSpeed(int a) {
		return problem.getAgentSpeed(a);
	}

	public float getCoalitionValue(int v, int[] coalition) {
		return problem.getCoalitionValue(v, coalition);
	}

	public float getWorkload(int v) {
		return workloads[v];
	}

	public int getDeadline(int v) {
		return problem.demands[v][0];
	}

	public Results getResults() {
		return results;
	}

	/**
	 * Returns the number of bytes needed to represent
	 * an input integer.
	 *
	 * @param n Primitive integer.
	 * @return Number of bytes.
	 */
	public static int bytesNeeded(int n) {
		if(n >= -128 && n <= 127)
	        return 1;
	    if(n >= -32768 && n <= 32767)
	    	return 2;
	    if(n >= -8388608 && n <= 8388607)
	        return 3;
	    return 4;
	}

}