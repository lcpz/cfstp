package exceptions;

public class NegativeWorkloadException extends Exception {

	private static final long serialVersionUID = 1L;

	public NegativeWorkloadException(String msg) {
		super(msg);
	}

}