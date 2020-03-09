package exceptions;

public class NotPositiveWorkloadException extends Exception {

	private static final long serialVersionUID = 1L;

	public NotPositiveWorkloadException(String msg) {
		super(msg);
	}

}