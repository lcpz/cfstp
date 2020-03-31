package solvers;

import java.util.Arrays;
import java.util.Random;

import org.apache.commons.math3.distribution.UniformIntegerDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import model.CFSTP;
import model.Results;

/**
 * Experimental setup of (Ramchurn et al., 2010).
 *
 * @author lcpz
 */
class SolversTest {

	static final boolean PRINT_PROBLEM = true;

	static final int TEST_REPETITIONS = 1;

	static final int AGENTS = 20;
	static final int TASKS = 300;
	static final int WORLD_DIM = 50;

	class MyProblem extends CFSTP {

		UniformRealDistribution unif = new UniformRealDistribution(1, 2);

		public MyProblem(int[] agents, int[] tasks, int[][] initialAgentLocations, int[][] taskLocations,
				int[][] demands) {
			super(agents, tasks, initialAgentLocations, taskLocations, demands);
		}

		@Override
		public int getAgentTravelTime(int agentId, int[] agentLocation, int[] taskLocation) {
			/* Manhattan distance, or l_1 norm; 1 grid per time step */
			return Math.abs(taskLocation[0] - agentLocation[0]) + Math.abs(taskLocation[1] - agentLocation[1]);
		}

		@Override
		public float getCoalitionValue(int task, int[] coalition) {
			return (float) (coalition.length * unif.sample());
		}

	}

	static CFSTP problem;

	static String[] solvers = new String[] { "CFLA", "CFLA2", "CTS" };

	// solver: avg, min, max
	static float[][] completedTaskPercentage = new float[solvers.length][3];
	static float[][] travelTime = new float[solvers.length][3];
	static float[][] completionTime = new float[solvers.length][3];
	static float[][] timesteps = new float[solvers.length][3];
	static long[][] computationalTime = new long[solvers.length][3]; // ms

	static {
		for (int i = 0; i < solvers.length; i++) {
			/* initialise minimum values */
			completedTaskPercentage[i][1] = 1;
			travelTime[i][1] = Float.MAX_VALUE;
			completionTime[i][1] = Float.MAX_VALUE;
			timesteps[i][1] = Float.MAX_VALUE;
			computationalTime[i][1] = Long.MAX_VALUE;
		}
	}

	@BeforeEach
	void setup() {
		Random r = new Random();
		int i;

		/* 0. basic data structures */
		int[] agents = new int[AGENTS];
		int[] tasks = new int[TASKS];

		for (i = 0; i < AGENTS; i++)
			agents[i] = i;

		for (i = 0; i < TASKS; i++)
			tasks[i] = i;

		/* 1a. random task positions and initial agent positions */
		int[][] taskLocations = new int[TASKS][2];
		for (i = 0; i < TASKS; i++) {
			taskLocations[i][0] = r.nextInt(WORLD_DIM);
			taskLocations[i][1] = r.nextInt(WORLD_DIM);
		}
		int[][] initialAgentLocations = new int[AGENTS][2];
		for (i = 0; i < AGENTS; i++) {
			initialAgentLocations[i][0] = r.nextInt(WORLD_DIM);
			initialAgentLocations[i][1] = r.nextInt(WORLD_DIM);
		}

		/* 1b. print positions */
		if (PRINT_PROBLEM) {
			for (i = 0; i < taskLocations.length; i++)
				System.out.println(
						String.format("task[%3d] at (%3d, %3d)", tasks[i], taskLocations[i][0], taskLocations[i][1]));
			for (i = 0; i < initialAgentLocations.length; i++)
				System.out.println(String.format("agent[%3d] starting at (%3d, %3d)", agents[i],
						initialAgentLocations[i][0], initialAgentLocations[i][1]));
		}

		/* 2a. generate deadlines and workloads */
		UniformIntegerDistribution unifD = new UniformIntegerDistribution(5, 600);
		UniformIntegerDistribution unifW = new UniformIntegerDistribution(10, 50);
		int[][] demands = new int[TASKS][2];
		for (i = 0; i < TASKS; i++) {
			demands[i][0] = unifD.sample();
			demands[i][1] = unifW.sample();
		}

		/* 2b. print demands */
		if (PRINT_PROBLEM)
			for (i = 0; i < TASKS; i++)
				System.out.println(String.format("task[%3d] has demands %s", i, Arrays.toString(demands[i])));

		/* 3. instantiate the solvers */
		problem = new MyProblem(agents, tasks, initialAgentLocations, taskLocations, demands);

	}

	@RepeatedTest(TEST_REPETITIONS)
	@DisplayName("Testing solvers")
	void test1() throws Exception {
		long t;
		Results r;
		Solver s = null;
		for (int i = 0; i < solvers.length; i++) {
			switch (solvers[i]) {
			case "EDF":
				s = new EDF(problem);
				break;
			case "CFLA":
				s = new CFLA(problem);
				break;
			case "CFLA2":
				s = new CFLA(problem, true);
				break;
			case "CTS":
				s = new CTS(problem);
				break;
			default:
				throw new Exception(String.format("%s is not a valid algorithm identifier", solvers[i]));
			}
			t = System.nanoTime();
			s.solve();
			t = System.nanoTime() - t; // extremely inaccurate with low numbers of repetitions, better to use JHM
			r = s.getResults();

			completedTaskPercentage[i][0] += r.getCompletedTaskPercentage();
			completedTaskPercentage[i][1] = Math.min(r.getCompletedTaskPercentage(), completedTaskPercentage[i][1]);
			completedTaskPercentage[i][2] = Math.max(r.getCompletedTaskPercentage(), completedTaskPercentage[i][2]);

			travelTime[i][0] += r.getAvgTravelTime();
			travelTime[i][1] = Math.min(r.getAvgTravelTime(), travelTime[i][1]);
			travelTime[i][2] = Math.max(r.getAvgTravelTime(), travelTime[i][2]);

			completionTime[i][0] += r.getAvgCompletionTime();
			completionTime[i][1] = Math.min(r.getAvgCompletionTime(), completionTime[i][1]);
			completionTime[i][2] = Math.max(r.getAvgCompletionTime(), completionTime[i][2]);

			timesteps[i][0] += s.getCurrentTime();
			timesteps[i][1] = Math.min(s.getCurrentTime(), timesteps[i][1]);
			timesteps[i][2] = Math.max(s.getCurrentTime(), timesteps[i][2]);

			computationalTime[i][0] += t;
			computationalTime[i][1] = Math.min(t, computationalTime[i][1]);
			computationalTime[i][2] = Math.max(t, computationalTime[i][2]);
		}
	}

	@AfterAll
	static void printResults() {
		float n, n_min, n_max;
		float n2, n2_min, n2_max;
		float n3, n3_min, n3_max;
		float n4, n4_min, n4_max;
		float n5, n5_min, n5_max;

		System.out.println(String.format(
		"\n[%d tests, %d tasks, %d agents, %d world_dim]\navg(completed tasks, agent travel time, task completion time, problem completion time, computational time)\n",
		TEST_REPETITIONS, TASKS, AGENTS, WORLD_DIM));

		for (int i = 0; i < solvers.length; i++) {
			n = completedTaskPercentage[i][0] / TEST_REPETITIONS;
			n_min = Math.abs(n - completedTaskPercentage[i][1]);
			n_max = Math.abs(completedTaskPercentage[i][2] - n);
			String s1 = String.format("%s%% \u00B1 [%s, %s]", nf(n*100), nf(n_min*100), nf(n_max*100));

			n2 = travelTime[i][0] / TEST_REPETITIONS;
			n2_min = Math.abs(n2 - travelTime[i][1]);
			n2_max = Math.abs(travelTime[i][2] - n2);
			String s2 = String.format("%s \u00B1 [%s, %s]", nf(n2), nf(n2_min), nf(n2_max));

			n3 = completionTime[i][0] / TEST_REPETITIONS;
			n3_min = Math.abs(n3 - completionTime[i][1]);
			n3_max = Math.abs(completionTime[i][2] - n3);
			String s3 = String.format("%s \u00B1 [%s, %s]", nf(n3), nf(n3_min), nf(n3_max));

			n4 = timesteps[i][0] / TEST_REPETITIONS;
			n4_min = Math.abs(n4 - timesteps[i][1]);
			n4_max = Math.abs(timesteps[i][2] - n4);
			String s4 = String.format("%s \u00B1 [%s, %s]", nf(n4), nf(n4_min), nf(n4_max));

			n5 = computationalTime[i][0] / (float) 1e6 / TEST_REPETITIONS;
			n5_min = Math.abs(n5 - computationalTime[i][1] / (float) 1e6);
			n5_max = Math.abs(computationalTime[i][2] / (float) 1e6 - n5);
			String s5 = String.format("%s ms \u00B1 [%s, %s]", nf(n5), nf(n5_min), nf(n5_max));

			System.out.println(
			String.format("%6s = (%s, %s, %s, %s, %s)", solvers[i], s1, s2, s3, s4, s5));
		}
	}

	private static String nf(float f) {
		return String.format("%.2f", f).replace(",", ".");
	}

}
