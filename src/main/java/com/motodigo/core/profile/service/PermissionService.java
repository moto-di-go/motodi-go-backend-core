package com.motodigo.core.profile.service;

import java.util.ArrayList;
import java.util.List;

import com.motodigo.core.profile.entity.ProfileRecord;
import com.motodigo.core.profile.entity.ProfileType;
import com.motodigo.core.profile.entity.ValidationStatus;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

	public List<String> allowedActions(ProfileRecord profile) {
		List<String> actions = new ArrayList<>();
		actions.add("PROFILE_READ");
		actions.add("PROFILE_UPDATE");
		actions.add("PROFILE_SWITCH");

		if (profile.validationStatus() == ValidationStatus.SUSPENDED) {
			return List.copyOf(actions);
		}

		switch (profile.type()) {
			case PASSENGER -> actions.addAll(List.of("PASSENGER_SEARCH_RIDE", "PASSENGER_BOOK_RIDE", "PASSENGER_PAY"));
			case MOTOTAXI -> addDriverActions(actions, profile.validationStatus(), "MOTOTAXI");
			case TRANSPORTER -> addTransporterActions(actions, profile.validationStatus());
			case AGENCY -> addAgencyActions(actions, profile.validationStatus());
			case ADMIN -> {
				if (profile.admin() != null) {
					actions.addAll(profile.admin().permissions());
				}
			}
		}
		return List.copyOf(actions);
	}

	private static void addDriverActions(List<String> actions, ValidationStatus status, String prefix) {
		if (status == ValidationStatus.VALIDATED) {
			actions.addAll(List.of(prefix + "_SET_AVAILABILITY", prefix + "_SHARE_POSITION", prefix + "_MANAGE_TRIP"));
		}
		else {
			actions.add("KYC_UPLOAD");
		}
	}

	private static void addTransporterActions(List<String> actions, ValidationStatus status) {
		if (status == ValidationStatus.VALIDATED) {
			actions.addAll(List.of("INTERCITY_ANNOUNCEMENT_WRITE", "INTERCITY_BOOKING_CONFIRM"));
		}
		else {
			actions.add("KYC_UPLOAD");
		}
	}

	private static void addAgencyActions(List<String> actions, ValidationStatus status) {
		if (status == ValidationStatus.VALIDATED) {
			actions.addAll(List.of("AGENCY_FLEET_MANAGE", "INTERCITY_ANNOUNCEMENT_WRITE", "INTERCITY_BOOKING_CONFIRM",
					"AGENCY_STATS_READ"));
		}
		else {
			actions.add("KYC_UPLOAD");
		}
	}
}
