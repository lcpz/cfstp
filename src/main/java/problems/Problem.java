package problems;

import locations.Location;
import model.CFSTP;

/**
 * A CFSTP instantiator.
 *
 * @author lcpz
 */
public class Problem {

	public static CFSTP getInstance(String type, int[] agents, int[] nodes, Location[] initialAgentLocations, Location[] nodeLocations, int[][] demands, int dynamismType) {
	    // the C prefix stands for Congested, while U stands for Urgent
		switch (type) {
		case "SUPERADDITIVE":
	        return new LinearPerturbedProblem(agents, nodes, initialAgentLocations, nodeLocations, demands, false, dynamismType);
		case "LINEAR_PERTURBED":
	        return new LinearPerturbedProblem(agents, nodes, initialAgentLocations, nodeLocations, demands, true, dynamismType);
		case "UNIFORM":
	        return new UniformProblem(agents, nodes, initialAgentLocations, nodeLocations, demands, false, dynamismType);
		case "NORMAL":
	        return new NormalProblem(agents, nodes, initialAgentLocations, nodeLocations, demands, false, dynamismType);
		case "MODIFIED_UNIFORM":
	        return new UniformProblem(agents, nodes, initialAgentLocations, nodeLocations, demands, true, dynamismType);
		case "MODIFIED_NORMAL":
	        return new NormalProblem(agents, nodes, initialAgentLocations, nodeLocations, demands, true, dynamismType);
		case "AGENT_BASED":
	        return new UCAgentBasedProblem(agents, nodes, initialAgentLocations, nodeLocations, demands, false, false, dynamismType);
		case "NDCS":
	        return new UCNDCSProblem(agents, nodes, initialAgentLocations, nodeLocations, demands, false, false, dynamismType);
		case "C_AGENT_BASED":
	        return new UCAgentBasedProblem(agents, nodes, initialAgentLocations, nodeLocations, demands, true, false, dynamismType);
		case "C_NDCS":
	        return new UCNDCSProblem(agents, nodes, initialAgentLocations, nodeLocations, demands, true, false, dynamismType);
		case "U_AGENT_BASED":
	        return new UCAgentBasedProblem(agents, nodes, initialAgentLocations, nodeLocations, demands, false, true, dynamismType);
		case "U_NDCS":
	        return new UCNDCSProblem(agents, nodes, initialAgentLocations, nodeLocations, demands, false, true, dynamismType);
		case "UC_AGENT_BASED":
	        return new UCAgentBasedProblem(agents, nodes, initialAgentLocations, nodeLocations, demands, true, true, dynamismType);
		case "UC_NDCS":
	        return new UCNDCSProblem(agents, nodes, initialAgentLocations, nodeLocations, demands, true, true, dynamismType);
		default:
			return null;
		}
	}

}