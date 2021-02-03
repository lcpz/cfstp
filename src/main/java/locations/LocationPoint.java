package locations;

import java.awt.Point;

/**
 * A location defined by a point in an Euclidean space.
 *
 * @author lcpz
 */
public class LocationPoint extends Location {

	private static final long serialVersionUID = 1L;

	public final Point location;

	public LocationPoint(int x, int y) {
		this.location = new Point(x, y);
	}

	public int getTravelTimeTo(Location destination, float speed) {
		try {
			if (destination instanceof LocationPoint)
				return (int) Math.ceil(location.distance(((LocationPoint) destination).location) / speed);
			else
				throw new UnsupportedOperationException(String.format("Destination is not a %s, but a %s",
				this.getClass().getSimpleName(), destination.getClass().getSimpleName()));
		} catch (UnsupportedOperationException e) {
			e.printStackTrace();
		}

		return -1;
	}

	@Override
	public String toString() {
		return String.format("Point(%d, %d)", location.x, location.y);
	}

}