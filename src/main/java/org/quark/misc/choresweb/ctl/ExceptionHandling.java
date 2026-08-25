package org.quark.misc.choresweb.ctl;

import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExceptionHandling {
	@ExceptionHandler(UnsupportedOperationException.class)
	public ProblemDetail handleUnsupportedOperation(UnsupportedOperationException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
		problem.setTitle("Operation Not Allowed");
		return problem;
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
		problem.setTitle("Bad Request Input");
		problem.setProperty("timestamp", System.currentTimeMillis());
		ex.printStackTrace();
		return problem;
	}

	@ExceptionHandler(NoSuchElementException.class)
	public ProblemDetail handleNoSuchElement(NoSuchElementException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setTitle("Item not found or not visible");
		return problem;
	}
}
