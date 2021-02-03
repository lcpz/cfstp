package model;

/**
 * A data structure that characterises the quality of a solution.
 *
 * @author lcpz
 */
public class Results {

	public static final String[] METRICS = {
		"messages sent",         // number of messages sent
		"network load (bytes)",          // total size of the messages sent
		"NCCCs",                 // number of non-concurrent constraint checks (Meisels, 2007)
		//"problemCompletionTime", // time at which the last node has been visited (only valid in static environments)
		//"medianCoalitionSize",   // median coalition size
		"visited nodes (%)"      // number of nodes visited / total number of nodes
	};

	public final float[] values;

	public Results(float[] values) {
		this.values = values;
	}

}