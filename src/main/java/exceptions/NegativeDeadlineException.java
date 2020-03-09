package exceptions;

public class NegativeDeadlineException extends Exception {

	private static final long serialVersionUID = 1L;

	public NegativeDeadlineException(String msg) {
		super(msg);
	}

}