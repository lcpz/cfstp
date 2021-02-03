package locations;

import com.javadocmd.simplelatlng.LatLng;
import com.javadocmd.simplelatlng.LatLngTool;
import com.javadocmd.simplelatlng.util.LengthUnit;

/**
 * A location defined by latitude and longitude coordinates.
 *
 * @author lcpz
 */
public class LocationLatLng extends Location {

	private static final long serialVersionUID = 1L;

	public final LatLng location;
	public final LengthUnit lengthUnit;

	public LocationLatLng(double latitude, double longitude, LengthUnit lengthUnit) {
		this.location = new LatLng(latitude, longitude);
		this.lengthUnit = lengthUnit;
	}

	public LocationLatLng(double latitude, double longitude) {
		this(latitude, longitude, LengthUnit.KILOMETER);
	}

	public int getTravelTimeTo(Location destination, float speed) {
		try {
			if (destination instanceof LocationLatLng)
				return (int) Math.ceil(LatLngTool.distance(location, ((LocationLatLng) destination).location, lengthUnit) / speed);
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
		return String.format("LatLng(%f, %f)", location.getLatitude(), location.getLongitude());
	}

}
