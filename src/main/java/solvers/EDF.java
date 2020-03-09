package solvers;

import java.util.Arrays;
import java.util.Comparator;

import org.apache.commons.lang3.ArrayUtils;

import model.Allocation;
import model.CFSTP;
import model.Results;

/**
 * Earliest-Deadline-First, a common scheduling algorithm (Ramamritham et al.,
 * 1989). In our variant, we allocate ECF coalitions to the tasks that have the
 * earliest deadlines first.
 *
 * @author lcpz
 */
public class EDF extends CFLA {

	protected int[] sortedTasks;

	public EDF(CFSTP problem) {
		super(problem);

		/* sort tasks by deadline, in ascending order */
		Integer[] s = ArrayUtils.toObject(tasks);
		Arrays.sort(s, new Comparator<Integer>() {
			public int compare(Integer o1, Integer o2) {
				if (demands[o1][0] < demands[o2][0])
					return -1;
				if (demands[o1][0] == demands[o2][0])
					return 0;
				return 1;
			}
		});

		sortedTasks = ArrayUtils.toPrimitive(s);
	}

	@Override
	protected int lookAhead(Allocation allocation, boolean[] isFreeAgent) {
		throw new UnsupportedOperationException("EDF does not have a look-ahead phase");
	}

	public void solve() {
		/* we assume that initial number of completed tasks is zero */
		int numberOfAllocatedTasks = 0;

		/* an index to keep track of the next earliest-deadline task */
		int earliestDeadlineTaskIdx = 0;

		int nextTask = -1;

		/* time steps at which agents are set 'free' */
		boolean[][] freeAt = new boolean[maxTaskDeadline][agents.length];

		do {
			/* set 'free' the agents that completed task in the previous time step */
			for (int a : agents)
				if (freeAt[currentTime][a])
					isBusyAgent[a] = false;

			/* define feasible allocations */
			setFeasibleAgentAllocationsToAllTasks();

			/* try all earliest-deadline unassigned tasks in sequence, until deadline */
			if (earliestDeadlineTaskIdx >= sortedTasks.length) {
				earliestDeadlineTaskIdx = 0;
				int i = 0;
				int[] temp = new int[sortedTasks.length];
				for (int j = 0; j < sortedTasks.length; j++)
					if (!isAllocatedTask[sortedTasks[j]] && demands[sortedTasks[j]][0] >= currentTime)
						temp[i++] = sortedTasks[j];
				sortedTasks = ArrayUtils.subarray(temp,  0,  i);
			}

			if (sortedTasks.length > 0)
				nextTask = sortedTasks[earliestDeadlineTaskIdx++];

			/* find an ECF coalition to allocate to nextTask */
			Allocation nextAllocation = ECF(nextTask);
			int[] nextCoalition = nextAllocation.getAgents();

			if (nextCoalition != ArrayUtils.EMPTY_INT_ARRAY) {
				int timeToSetFree = nextAllocation.getCompletionTime() + 1;

				for (int agent : nextCoalition) {
					isBusyAgent[agent] = true;
					if (timeToSetFree <= maxTaskDeadline)
						freeAt[timeToSetFree][agent] = true;
					agentLocations[agent] = taskLocations[nextTask];
				}

				isAllocatedTask[nextTask] = true;
				numberOfAllocatedTasks++;
				avgTravelTime += nextAllocation.getTravelTime();
				avgCompletionTime += nextAllocation.getCompletionTime() - currentTime;
			}

			if (DEBUG)
				printCurrentAllocation(nextAllocation, numberOfAllocatedTasks);

			currentTime++;
		} while (sortedTasks.length > 0 && numberOfAllocatedTasks < tasks.length && currentTime < maxTaskDeadline);

		if (numberOfAllocatedTasks != 0) {
			avgTravelTime /= numberOfAllocatedTasks;
			avgCompletionTime /= numberOfAllocatedTasks;
		}

		results = new Results(avgTravelTime, avgCompletionTime, numberOfAllocatedTasks / (float) tasks.length);
	}

}