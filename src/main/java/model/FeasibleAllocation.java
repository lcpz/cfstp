package model;

public class FeasibleAllocation extends Allocation {

	private int allocationTime;
	private int[] arrivalTimes;

	public FeasibleAllocation(int allocationTime, int task, int[] agents, int[] arrivalTimes) {
		this.allocationTime = allocationTime;
		this.task = task;
		this.agents = agents;
		this.arrivalTimes = arrivalTimes;
	}

	public int getAllocationTime() {
		return allocationTime;
	}

	public int[] getArrivalTimes() {
		return arrivalTimes;
	}

}