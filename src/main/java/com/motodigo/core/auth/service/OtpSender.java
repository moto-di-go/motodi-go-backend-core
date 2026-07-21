package com.motodigo.core.auth.service;

public interface OtpSender {

	void sendOtp(String phoneNumber, String message);
}
