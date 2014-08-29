package com.javax0.clalotils;

public class RedundantClassPathException extends Exception {

	private static final long serialVersionUID = -9113234232735720438L;

	public RedundantClassPathException() {
	}

	public RedundantClassPathException(String message) {
		super(message);
	}

	public RedundantClassPathException(Throwable cause) {
		super(cause);
	}

	public RedundantClassPathException(String message, Throwable cause) {
		super(message, cause);
	}

	public RedundantClassPathException(String message, Throwable cause,
			boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
