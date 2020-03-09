package model;

/**
 * Data structure containing the results of a CFSTP solver, namely: average
 * travel and completion time; completed task percentage.
 *
 * @author lcpz
 */
public class Results {

	private float avgTravelTime, avgCompletionTime, completedTaskPercentage;

	public Results(float avgTravelTime, float avgCompletionTime, float completedTaskPercentage) {
		this.avgTravelTime = avgTravelTime;
		this.avgCompletionTime = avgCompletionTime;
		this.completedTaskPercentage = completedTaskPercentage;
	}

	public float getAvgTravelTime() {
		return avgTravelTime;
	}

	public float getAvgCompletionTime() {
		return avgCompletionTime;
	}

	public float getCompletedTaskPercentage() {
		return completedTaskPercentage;
	}

}
