package com.motodigo.core.auth.exception;

import java.time.Instant;
import java.util.stream.Collectors;

import com.motodigo.core.auth.dto.ErrorResponse;
import com.motodigo.core.booking.exception.BookingException;
import com.motodigo.core.geo.exception.GeoException;
import com.motodigo.core.intercity.exception.IntercityException;
import com.motodigo.core.kyc.exception.KycException;
import com.motodigo.core.notification.exception.NotificationException;
import com.motodigo.core.payment.exception.PaymentException;
import com.motodigo.core.pricing.exception.PricingException;
import com.motodigo.core.wallet.exception.WalletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class AuthExceptionHandler {

	private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

	private final MessageSource messageSource;

	public AuthExceptionHandler() {
		this.messageSource = null;
	}

	public AuthExceptionHandler(MessageSource messageSource) {
		this.messageSource = messageSource;
	}

	@ExceptionHandler(AuthException.class)
	ResponseEntity<ErrorResponse> handleAuthException(AuthException exception, HttpServletRequest request) {
		return error(exception.errorCode().status(), exception.errorCode().name(), exception.getMessage(), request);
	}

	@ExceptionHandler(KycException.class)
	ResponseEntity<ErrorResponse> handleKycException(KycException exception, HttpServletRequest request) {
		return error(exception.errorCode().status(), exception.errorCode().name(), exception.getMessage(), request);
	}

	@ExceptionHandler(WalletException.class)
	ResponseEntity<ErrorResponse> handleWalletException(WalletException exception, HttpServletRequest request) {
		return error(exception.errorCode().status(), exception.errorCode().name(), exception.getMessage(), request);
	}

	@ExceptionHandler(PaymentException.class)
	ResponseEntity<ErrorResponse> handlePaymentException(PaymentException exception, HttpServletRequest request) {
		return error(exception.errorCode().status(), exception.errorCode().name(), exception.getMessage(), request);
	}

	@ExceptionHandler(IntercityException.class)
	ResponseEntity<ErrorResponse> handleIntercityException(IntercityException exception, HttpServletRequest request) {
		return error(exception.errorCode().status(), exception.errorCode().name(), exception.getMessage(), request);
	}

	@ExceptionHandler(BookingException.class)
	ResponseEntity<ErrorResponse> handleBookingException(BookingException exception, HttpServletRequest request) {
		return error(exception.errorCode().status(), exception.errorCode().name(), exception.getMessage(), request);
	}

	@ExceptionHandler(NotificationException.class)
	ResponseEntity<ErrorResponse> handleNotificationException(NotificationException exception,
			HttpServletRequest request) {
		return error(exception.errorCode().status(), exception.errorCode().name(), exception.getMessage(), request);
	}

	@ExceptionHandler(GeoException.class)
	ResponseEntity<ErrorResponse> handleGeoException(GeoException exception, HttpServletRequest request) {
		return error(exception.errorCode().status(), exception.errorCode().name(), exception.getMessage(), request);
	}

	@ExceptionHandler(PricingException.class)
	ResponseEntity<ErrorResponse> handlePricingException(PricingException exception, HttpServletRequest request) {
		return error(exception.errorCode().status(), exception.errorCode().name(), exception.getMessage(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + " " + error.getDefaultMessage())
				.collect(Collectors.joining("; "));
		if (message.isBlank()) {
			message = AuthErrorCode.VALIDATION_FAILED.message();
		}
		return error(HttpStatus.BAD_REQUEST, AuthErrorCode.VALIDATION_FAILED.name(), message, request);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, AuthErrorCode.VALIDATION_FAILED.name(), exception.getMessage(), request);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
			HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, AuthErrorCode.VALIDATION_FAILED.name(),
				"Parametre invalide: " + exception.getName(), request);
	}

	private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message, HttpServletRequest request) {
		return ResponseEntity.status(status)
				.body(new ErrorResponse(code, message, request.getHeader(CORRELATION_ID_HEADER), Instant.now()));
	}
}
