package locations;

import java.io.Serializable;

/**
 * A generic location.
 *
 * @author lcpz
 */
public abstract class Location implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * Time units required to travel from one location to another ($\rho$).
	 *
	 * @param departure   The initial location.
	 * @param destination The arrival location.
	 * @param speed       The travel speed.
	 *
	 * @return The time units required to reach destination from departure.
	 */
	public static int getTravelTime(Location departure, Location destination, float speed) {
		return departure.getTravelTimeTo(destination, speed);
	}

	/**
	 * Time units required to travel from this location to the input destination
	 * ($\rho$).
	 *
	 * @param destination The arrival location.
	 * @param speed       The travel speed.
	 *
	 * @return The time units required to reach destination from this location.
	 */
	public abstract int getTravelTimeTo(Location destination, float speed);

}
