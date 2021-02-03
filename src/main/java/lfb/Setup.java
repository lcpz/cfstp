package lfb;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.math3.distribution.UniformIntegerDistribution;

import locations.Location;
import locations.LocationLatLng;
import model.CFSTP;
import problems.Problem;

/**
 * CFSTP instances generated with the London Fire Brigade records.
 *
 * @author lcpz
 */
public class Setup {

	private String charSplit;

	/* The first line contains the headers. */
	public final String[][] nodeRecords;

	public final Map<String, ArrayList<String>> stationsMap;

	protected int currIdx; // current record index

	private ThreadLocalRandom rnd = ThreadLocalRandom.current();

	private UniformIntegerDistribution unifW = new UniformIntegerDistribution(10, 300);

	public Setup(Path taskDatasetPath, Path stationLocationDatasetPath, String charSplit) {
		this.charSplit = charSplit;
		String label = String.format("[%s]", this.getClass().getSimpleName());
		System.out.println(String.format("%s node dataset: %s", label, taskDatasetPath.toString()));
		System.out.println(String.format("%s station dataset: %s", label, stationLocationDatasetPath.toString()));
		System.out.print(String.format("%s extracting task records... ", label));
		nodeRecords = extract(taskDatasetPath);
		//currIdx = ThreadLocalRandom.current().nextInt(1, nodeRecords.length); // random initial record
		currIdx = 1;
		System.out.print(String.format("done\n%s extracting station records... ", label));
		stationsMap = extractMap(stationLocationDatasetPath);
		System.out.println(String.format("done\n%s initialisation completed", label));
	}

	public Setup(String nodeP, String stationP) {
		this(Paths.get(nodeP), Paths.get(stationP), ",");
	}

	private String[][] extract(Path path) {
		BufferedReader br = null;

		try {
			br = new BufferedReader(new FileReader(path.toFile()));

			String[][] s = new String[(int) Files.lines(path).count()][];
			String line;

			int i = 0;
			while ((line = br.readLine()) != null)
				s[i++] = line.split(charSplit);

			return s;
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null)
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}

		return null;
	}

	private Map<String, ArrayList<String>> extractMap(Path path) {
		BufferedReader br = null;

		try {
			br = new BufferedReader(new FileReader(path.toFile()));

			Map<String, ArrayList<String>> m = new HashMap<>();
			String line;
			String[] arr;
			ArrayList<String> al;
			String value;

			while ((line = br.readLine()) != null) {
				arr = line.split(charSplit);
				al = new ArrayList<>(arr.length);
				for (String col : arr)
					al.add(col);
				value = al.remove(0);
				m.put(value, al);
			}

			return m;
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null)
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}

		return null;
	}

	/**
	 * Generate a CFSTP instance.
	 *
     * As of December 2020, the active LFB pumping appliances are 150 Mercedes
     * Benz Atego 1325F. Hence, in this benchmark, all agents have the same speed.
     *
     * This is not an unreasonable assumption, since if an algorithm is better
     * than others with homogeneous agent speeds, then it is also better with
     * heterogeneous agent speeds.
	 *
	 * @param type         The CFSTP type.
	 * @param agentNr      The number of agents in the instance.
	 * @param ratio        The nodes-agent ratio.
	 * @param dynamismType The type of dynamism of the problem.
	 * @return A CFSTP instance.
	 */
	public CFSTP generate(String type, int agentNr, float ratio, int dynamismType) {
		if (currIdx >= nodeRecords.length)
			currIdx = 1;

		int[] nodes = new int[(int) Math.ceil(agentNr * ratio)];
		int i, attendance;
		ArrayList<LocationLatLng> possibleAgentLocations = new ArrayList<>();
		ArrayList<String> station;
		Location[] nodeLocations = new Location[nodes.length];
		int[][] demands = new int[nodes.length][2];

		for (i = 0; i < nodes.length; i++) {
			nodeLocations[i] = new LocationLatLng(Double.parseDouble(nodeRecords[currIdx][4]),
			Double.parseDouble(nodeRecords[currIdx][5]));

			station = stationsMap.get(nodeRecords[currIdx][9]);
			if (station == null)
				System.err.println(String.format("Input station dataset does not contain %s", nodeRecords[currIdx][9]));
			else
				possibleAgentLocations.add(new LocationLatLng(Double.parseDouble(station.get(1)), Double.parseDouble(station.get(2))));

			attendance = Integer.parseInt(nodeRecords[currIdx][8]); // in seconds

			if (attendance == 0) {
				System.err.println(String.format("WARN: zero attendance for record %s", nodeRecords[currIdx][0]));
				System.err.println("Setting a random integer in [300, 900]"); // 5 - 15 mins
				attendance = rnd.nextInt(300, 901);
			}

			demands[i][0] = (int) Math.ceil(attendance); // deadline
			demands[i][1] = unifW.sample(); // workload
			nodes[i] = i;

			if (++currIdx >= nodeRecords.length)
				currIdx = 1; // we re-start from 1 since row 0 contains the headers
		}

		int[] agents = new int[agentNr];
		Location[] initialAgentLocations = new Location[agentNr];
		for (i = 0; i < agentNr; i++) {
			agents[i] = i;
			initialAgentLocations[i] = possibleAgentLocations.get(ThreadLocalRandom.current().nextInt(possibleAgentLocations.size()));
		}

		return Problem.getInstance(type, agents, nodes, initialAgentLocations, nodeLocations, demands, dynamismType);
	}

}