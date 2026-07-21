package com.motodigo.core.auth.entity;

public enum AuthEventType {
	REGISTER,
	OTP_SEND,
	OTP_VERIFY,
	LOGIN,
	REFRESH,
	LOGOUT,
	PASSWORD_FORGOT,
	PASSWORD_RESET
}
