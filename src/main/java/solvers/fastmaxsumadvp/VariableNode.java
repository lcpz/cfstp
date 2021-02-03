package solvers.fastmaxsumadvp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import solvers.Solver;
import toolkit.Utils;

public class VariableNode {

	/**
	 * Limits the number of Q messages computed to improve the computational
	 * time.
	 */
	//public final int LIMITER = 10; // maximum number of possible node allocations

	public final Integer agentIdx;
	public final List<Integer> domain;

	private Integer x; // current solution
	private Float b; // belief (or marginal function) of x
	private List<Float> beliefs; // belief of each element in domain

	/* Last messages sent to each function in the domain of this agent. */
	private Map<Integer, Map<Integer, Float>> Q;

	public VariableNode(Integer agentIdx, List<Integer> domain) {
		this.agentIdx = agentIdx;
		this.domain = domain;

		x = domain.get(ThreadLocalRandom.current().nextInt(domain.size())); // random initialisation
		b = 0f; // R messages are initially all 0

		// initialise to 0 the beliefs and the Q messages to the function node in the domain of agentIdx
		beliefs = new ArrayList<>(domain.size());
		Q = new HashMap<>();
		for (Integer nodeIdx : domain) {
			beliefs.add(0f);
			Q.put(nodeIdx, Utils.getZeroMessages(domain));
		}
	}

	public Integer getX() {
		return x;
	}

	public Float getBelief() {
		return b;
	}

	public Float getQ(Integer nodeIdx, Integer d) {
		if (Q.get(nodeIdx) != null)
			return Q.get(nodeIdx).get(d);

		if (Solver.DEBUG)
			System.err.println(String.format("Variable node %d does not have the Q-row of node %d", agentIdx, nodeIdx));

		return 0f;
	}

	private boolean setQ(Integer nodeIdx, Integer d, Float q) {
		Map<Integer, Float> Qnode = Q.get(nodeIdx);
		Qnode.put(d, q);
		return Q.put(nodeIdx, Qnode) != null;
	}

	public void sendQMessageTo(Integer nodeIdx, Map<Integer, FunctionNode> functionNodeMap) {
		float alpha = 0, q;
		//int limitCounter;

		for (Integer d : domain) {
			q = 0;

			//limitCounter = 0;
			for (Integer d2 : domain)
				//if (limitCounter++ < LIMITER && d2 != d)
				if (d2 != d)
					q += functionNodeMap.get(nodeIdx).getR(agentIdx, d);

			alpha += q;

			setQ(nodeIdx, d, q);
		}

		alpha /= domain.size();

		for (Integer d : domain)
			setQ(nodeIdx, d, Q.get(nodeIdx).get(d) - alpha);
	}

	/**
	 * Computes beliefs and assignments based on the R messages of given function nodes.
	 *
	 * @param functionNodMap A FunctionNode map.
	 */
	public void computeAssignment(Map<Integer, FunctionNode> functionNodeMap) {
		Integer d;
		Float sum;

		b = Float.POSITIVE_INFINITY; // assuming a minimisation problem

		for (int i = 0; i < domain.size(); i++) {
			d = domain.get(i);
			sum = 0f;

			for (Integer nodeIdx : domain) // for each neighbour of agentIdx
				sum += functionNodeMap.get(nodeIdx).getR(agentIdx, d);

			beliefs.set(i, sum);

			if (sum < b) {
				b = sum;
				x = d;
			}
		}
	}

}