package locations;

/**
 * A location defined as a point in a matrix.
 *
 * @author lcpz
 */
public class LocationManhattan extends Location {

	private static final long serialVersionUID = 1L;

	public final int x, y;

	public LocationManhattan(int x, int y) {
		this.x = x;
		this.y = y;
	}

	@Override
	public int getTravelTimeTo(Location destination, float speed) {
		try {
			if (destination instanceof LocationManhattan) {
				int x2 = ((LocationManhattan) destination).x;
				int y2 = ((LocationManhattan) destination).y;
				return (int) Math.ceil((Math.abs(x - x2) + Math.abs(y + y2)) / speed);
			} else
				throw new UnsupportedOperationException(String.format("Destination is not a %s, but a %s",
				this.getClass().getSimpleName(), destination.getClass().getSimpleName()));
		} catch (UnsupportedOperationException e) {
			e.printStackTrace();
		}

		return -1;
	}

	@Override
	public String toString() {
		return String.format("Manhattan(%d, %d)", x, y);
	}

}