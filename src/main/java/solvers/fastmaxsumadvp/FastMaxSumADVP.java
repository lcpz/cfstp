package solvers.fastmaxsumadvp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import model.CFSTP;
import solvers.Solver;
import toolkit.Utils;

/**
 * An implementation of Max-Sum_ADVP for solving CFSTPs.
 *
 * We assume that each agent controls exactly 1 decision node and at most
 * 1 function node.
 *
 * We do not use the exploration heuristic methods of Zivan et al.
 *
 * @author lcpz
 */
public class FastMaxSumADVP extends Solver {

	public class Edge {

		public final int node1, node2; // node indexes
		public final int type;

		/**
		 * Creates a factor graph edge, oriented from node1 to node2.
		 *
		 * If type = 0, then node1 is a variable node and
		 * node2 is a function node.
		 *
		 * Else if type = 1, then node1 is a function node and
		 * node2 is a variable node.
		 *
		 * @param node1 A factor graph node.
		 * @param node2 A factor graph node.
		 * @param type Type of the edge (variable-function or viceversa).
		 */
		public Edge(int node1, int node2, int type) {
			this.node1 = node1;
			this.node2 = node2;
			this.type = type;
		}

	}

	public static final Comparator<Edge> SortByIndex = new Comparator<>() {
		public int compare(Edge a, Edge b) {
			if (a.node1 == b.node1)
				return a.node2 - b.node2;
			return a.node1 - b.node1;
		}
	};

	/*
	 * Each Max-Sum message contains a node address for each domain element (8
	 * bytes in Java).
	 */
	public static final int BASE_MESSAGE_SIZE = 8;

	/* The current factor graph */
	protected Map<Integer, VariableNode> variableNodeMap;
	protected Map<Integer, FunctionNode> functionNodeMap;

	public FastMaxSumADVP(CFSTP problem) {
		super(problem);
	}

	@Override
	public int solve(int t) {
		/*
		 * 1. Create variable domains and function scopes, thus satisfying
		 * structural and spatial constraints.
		 *
		 * We use agent and node indexes instead of the actual IDs.
		 */
		Map<Integer, List<Integer>> freeAgents = new HashMap<>(), functions = new HashMap<>();
		List<Integer> freeAgentDomain, functionScope;
		int allocableAgents = 0;
		for (int a = 0; a < agents.length; a++) {
			/* if the problem is static or the agent is enabled */
			if (problem.dynamismType == 0 || (problem.dynamismType == 1 && problem.enabled[a]) || problem.dynamismType == 2) {
				/* compute the variable domain of each free agent */
				if (agentStatus[a] == AgentStatus.FREE) {
					freeAgentDomain = getFreeAgentDomain(a);

					if (freeAgentDomain.size() == 0)
						continue; // this free agent cannot reach any node v by deadline d_v

					/* also, compute each function scope (i.e., define the variables in each constraint) */
					for (Integer nodeIdx : freeAgentDomain) {
						functionScope = functions.get(nodeIdx);
						if (functionScope == null)
							functionScope = new ArrayList<>();
						functionScope.add(Integer.valueOf(a));
						functions.put(nodeIdx, functionScope);
					}

					freeAgents.put(Integer.valueOf(a), freeAgentDomain);

					allocableAgents++;
				}
				/* otherwise, if a reached its assigned node, update its status */
				else if (reachingNode[a][0] > -1 && assignmentStatus[a][reachingNode[a][0]] == AssignmentStatus.REACHING)
					updateAgentStatus(a);
			} else if (problem.dynamismType == 1 && reachingNode[agents[a]][0] > -1) // remove a from the system
				removeAgent(a);
		}

		if (allocableAgents > 0) {
			/* 2. Create the factor graph and the node order (by index). */
			TreeSet<Edge> edges = createDAG(freeAgents, functions);

			/*
			 * 3. Message propagation phases.
			 *
			 * Since our implementation is centralised, we do not need to perform L
			 * propagations per phase, where L is the diameter of the DAG.
			 *
			 * Our agents know the DAG order, hence we need just 1 iteration
			 * following the order of the factor graph <code>edges</code>.
			 *
			 * The temporal constraints are satisfied while sending R messages
			 * from function nodes to variable nodes.
			 */

			// AD
			sendMessages(edges, false, false); // first phase AD: ascending order
			sendMessages(edges.descendingSet(), true, false); // second phase AD: descending order

			// ADVP
			sendMessages(edges, false, true); // first phase ADVP: ascending order
			sendMessages(edges.descendingSet(), true, true); // second Phase ADVP: descending order

			/* 4. Compute value assignments and coalition allocation data structures. */
			VariableNode variableNode;
			Integer assignment;
			HashMap<Integer, List<Integer>> coalitionAllocations = new HashMap<>();
			List<Integer> coalition;
			for (Map.Entry<Integer, VariableNode> entry : variableNodeMap.entrySet()) {
				variableNode = entry.getValue();
				variableNode.computeAssignment(functionNodeMap);
				assignment = variableNode.getX(); // nodeIdx

				coalition = coalitionAllocations.get(assignment); // coalition of nodeIdx
				if (coalition == null)
					coalition = new ArrayList<>();
				coalition.add(entry.getKey()); // add agentIdx to coalition
				coalitionAllocations.put(assignment, coalition);
			}

			/*
			 * 5. Allocate nodes to agents and update each agent status, thus
			 * satisfying the structural constraints.
			 */
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

	private TreeSet<Edge> createDAG(Map<Integer, List<Integer>> freeAgents, Map<Integer, List<Integer>> functions) {
		TreeSet<Edge> edges = new TreeSet<>(SortByIndex);
		variableNodeMap = new HashMap<>();
		functionNodeMap = new HashMap<>();
		int prevSize;
		Integer agentIdx;
		List<Integer> domain;
		for (Map.Entry<Integer, List<Integer>> entry : freeAgents.entrySet()) {
			agentIdx = entry.getKey();
			domain = entry.getValue();
			variableNodeMap.put(agentIdx, new VariableNode(agentIdx, domain));
			for (Integer nodeIdx : domain) {
				/*
				 * If it exists an agent a2 in the scope of v and is such that
				 * a < a2, then add edge (a, v).
				 */
				prevSize = edges.size();
				for (Integer agentIdx2 : functions.get(nodes[nodeIdx]))
					if (agentIdx < agentIdx2) { // add edge (a, v) to the DAG
						edges.add(new Edge(agentIdx, nodeIdx, 0)); // 0 means variable-function edge
						break;
					}
				/*
				 * Otherwise, either a is the only agent in the scope of v, or
				 * it exists an agent a2 in the scope of v and is such that
				 * a2 < a. In this case, add edge (v, a).
				 */
				if (edges.size() == prevSize)
					edges.add(new Edge(agentIdx, nodeIdx, 1)); // 1 means function-variable edge
			}
		}

		Integer nodeIdx;
		for (Map.Entry<Integer, List<Integer>> entry : functions.entrySet()) {
			nodeIdx = entry.getKey();
			functionNodeMap.put(nodeIdx, new FunctionNode(nodeIdx.intValue(), entry.getValue()));
		}

		return edges;
	}

	private void sendMessages(Set<Edge> edges, boolean isReversed, boolean valuePropagation) {
		Integer sender, receiver; // node indexes
		int type; // type of edge

		/*
		 * The AD propagation is a sequential/synchronised operation.
		 *
		 * Because of this, for each message there is 1 NCCC.
		 *
		 * Additionally, for each function-to-variable message, there are n NCCCs more,
		 * where n is the number of constraint checks done while creating R messages.
		 */

		FunctionNode functionNode;
		VariableNode variableNode;

		for (Edge e : edges) {
			if (!isReversed) {
				sender = Integer.valueOf(e.node1);
				receiver = Integer.valueOf(e.node2);
				type = e.type;
			} else {
				sender = Integer.valueOf(e.node2);
				receiver = Integer.valueOf(e.node1);
				if (e.type == 0)
					type = 1;
				else
					type = 0;
			}

			if (type == 0) { // variable -> function
				variableNode = variableNodeMap.get(sender);

				if (variableNode == null || !variableNode.domain.contains(receiver))
					continue;

				variableNode.sendQMessageTo(receiver, functionNodeMap);
				messagesSent += variableNode.domain.size();
				networkLoad += variableNode.domain.size() * BASE_MESSAGE_SIZE;
				NCCCs++;
				if (valuePropagation) {
					variableNode.computeAssignment(functionNodeMap);
					functionNode = functionNodeMap.get(receiver);
					functionNode.setPartialAssignment(sender, variableNode.getX());
					networkLoad += BASE_MESSAGE_SIZE; // the partial assignment is a domain element
				}
			} else { // function -> variable
				functionNode = functionNodeMap.get(sender);

				if (functionNode == null || !functionNode.scopeMap.containsKey(receiver))
					continue;

				NCCCs += functionNode.sendRMessageTo(receiver, variableNodeMap, this, valuePropagation);
				messagesSent += functionNode.scope.length;
				networkLoad += functionNode.scope.length * BASE_MESSAGE_SIZE;
			}
		}
	}

}