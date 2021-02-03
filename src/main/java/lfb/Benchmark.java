package lfb;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.rank.Median;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Stopwatch;

import model.CFSTP;
import model.Results;
import solvers.CTS;
import solvers.DsaSDP;
import solvers.Solver;
import solvers.fastmaxsumadvp.FastMaxSumADVP;

/**
 * London Fire Brigade (LFB) benchmark.
 *
 * @author lcpz
 */
public class Benchmark {

	protected Setup lfb;
	protected Mean mean = new Mean();
	protected StandardDeviation std = new StandardDeviation();
	protected Median median = new Median();

	// algorithms - metrics (plus CPU time) - replicates
	protected double[][][] batchResults;

	private int testNr = 1;

	private static Stopwatch stopwatch = Stopwatch.createUnstarted();

	protected void run(String type, float ratio, int batchIdx) {
		CFSTP problem = lfb.generate(type, agents, ratio, dynamismType);

		for (int i = 0; i < algorithms.size(); i++)
			switch (algorithms.get(i)) {
			case "CTS": // Cluster-based Task Scheduling
				getAndPrintResults(new CTS(problem.clone()), i, batchIdx);
				break;
			case "FMS-ADVP": // Fast Max-Sum on Alternating Directed acyclic graph and Value Propagation
				getAndPrintResults(new FastMaxSumADVP(problem.clone()), i, batchIdx);
				break;
			case "DSA-SDP": // Distributed Stochastic Algorithm with Slope Dependent Probability
				getAndPrintResults(new DsaSDP(problem.clone()), i, batchIdx);
				break;
			default:
				System.err.println(String.format("%s is not a valid algorithm ID", algorithms.get(i)));
				break;
			}

		testNr++;
	}

	private void getAndPrintResults(Solver solver, int i, int batchIdx) {
		stopwatch.reset();
		stopwatch.start();
		solver.solve();
		stopwatch.stop();

		Results results = solver.getResults();

		StringBuilder s = new StringBuilder(String.format("[%5d] %s [", testNr, algorithms.get(i)));

		int k;
		for (k = 0; k <= results.values.length - 1; k++) {
			batchResults[i][k][batchIdx] = results.values[k];
			s.append(String.format("%s: %s, ", Results.METRICS[k], results.values[k]));
		}
		batchResults[i][k][batchIdx] = (float) stopwatch.elapsed(TimeUnit.MILLISECONDS);
		s.append(String.format("CPU time (ms): %s]", batchResults[i][k][batchIdx]));

		System.out.println(s.toString());
	}

	public double getCI(double[] values) { // 95% Confidence Interval
		return 1.96d * (std.evaluate(values) * ((double) Math.sqrt(values.length)));
	}

	public String getCIMedian(double[] values) { // like above, but for median
		Arrays.sort(values);

		double v = 1.96 * Math.sqrt(values.length * 0.5 * 0.95);
		int j = (int) Math.ceil(values.length * 0.5 - v);
		int k = (int) Math.ceil(values.length * 0.5 + v);

		if (j < 0)
			j = 0;

		if (k >= values.length)
			k = values.length - 1;

		double m = median.evaluate(values);

		return String.format("%.2f +- [%.2f %.2f]", m, values[k] - m, m - values[j]);
	}

	protected void reset(String type, float ratio) {
		StringBuilder s;
		int i, j;

		for (i = 0; i < algorithms.size(); i++) {
			s = new StringBuilder(String.format("\n%s-%s %s [", type, ratio, algorithms.get(i)));
			for (j = 0; j <batchResults[i].length - 1; j++)
				s.append(String.format("%s: %s, ", Results.METRICS[j], getCIMedian(batchResults[i][j])));

			s.append(String.format("CPU time (ms): %s]", getCIMedian(batchResults[i][j])));
			System.out.print(s.toString());

			batchResults[i] = new double[Results.METRICS.length + 1][replicates];
		}

		lfb.currIdx = 1;

		System.out.println();
	}

	@Parameter(names = { "--node-dataset-path", "-n" }, required = true, description = "Path to a London Fire Brigade data set containing nodes (tasks)")
	private String nodeDatasetPath;

	@Parameter(names = { "--station-dataset-path", "-s" }, required = true, description = "Path to a London Fire Brigade data set containing fire stations")
	private String stationDatasetPath;

	@Parameter(names = { "--problem-types", "-p" }, variableArity = true, description = "The types of problem to test with (See README for the full list)")
	private List<String> problemClasses = Arrays.asList(new String[] {
		"AGENT_BASED",
		"NDCS",
		"C_AGENT_BASED",
		"C_NDCS",
		"U_AGENT_BASED",
		"U_NDCS",
		"UC_AGENT_BASED",
		"UC_NDCS"
	});

	@Parameter(names = { "--dynamism-type", "-d" }, description = "An integer expressing if environment is static (0), agents decrease (1) or nodes increase (2)")
	private int dynamismType;

	@Parameter(names = { "--replicates", "-i" }, description = "How many replicates per test configuration")
	private int replicates = 100;

	@Parameter(names = { "--agents", "-m" }, description = "Number of agents for each problem")
	private int agents = 150;

	@Parameter(names = { "--node-to-agent-ratios", "-r" }, variableArity = true, description = " The node-to-agent ratio (i.e., how many nodes per each agent)")
	private List<Float> ratios = Arrays.asList(new Float[] {
		1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f,
		11f, 12f, 13f, 14f, 15f, 16f, 17f, 18f, 19f, 20f
	});

	@Parameter(names = { "--algorithms", "-a" }, variableArity = true, description = "Algorithms to test (See README for the full list)")
	private List<String> algorithms = Arrays.asList(new String[] {
		//"DSA-SDP", "FMS-ADVP", "CTS"
		"DSA-SDP", "CTS"
	});

    @Parameter(names = { "--help", "-h" }, description = "Print this help text", help = true)
    private boolean help = false;

	public static void main(String[] args) {
		Benchmark benchmark = new Benchmark();
		JCommander jct = JCommander.newBuilder().addObject(benchmark).build();
		jct.parse(args);

		if (benchmark.help) {
			jct.usage();
			return;
		}

		benchmark.batchResults = new double[benchmark.algorithms.size()][Results.METRICS.length + 1][benchmark.replicates];

		benchmark.lfb = new Setup(benchmark.nodeDatasetPath, benchmark.stationDatasetPath);

		System.out.println(String.format("[Benchmark] fixed number of agents: %d", benchmark.agents));
		System.out.println(String.format("[Benchmark] node-to-agent ratios: %s", benchmark.ratios));
		System.out.println(String.format("[Benchmark] algorithms: %s", benchmark.algorithms));
		System.out.println(String.format("[Benchmark] replicates: %d", benchmark.replicates));
		System.out.println(String.format("[Benchmark] dynamism: %d", benchmark.dynamismType));
		System.out.println(String.format("[Benchmark] problem classes: %s", benchmark.problemClasses));

		System.out.println("[Benchmark] session started\n");

		int i;
		for (String problemClass : benchmark.problemClasses)
			for (Float ratio : benchmark.ratios) {
				for (i = 0; i < benchmark.replicates; i++)
					benchmark.run(problemClass, ratio, i);
				benchmark.reset(problemClass, ratio);
				System.out.println();
			}

		System.out.println("[Benchmark] session completed");
	}
}