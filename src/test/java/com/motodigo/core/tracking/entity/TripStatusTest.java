package com.motodigo.core.tracking.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class TripStatusTest {

	@ParameterizedTest
	@CsvSource({
			"CONFIRMED,APPROACHING,true",
			"CONFIRMED,CANCELLED,true",
			"CONFIRMED,ON_BOARD,false",
			"CONFIRMED,COMPLETED,false",
			"APPROACHING,ON_BOARD,true",
			"APPROACHING,CANCELLED,true",
			"APPROACHING,COMPLETED,false",
			"APPROACHING,CONFIRMED,false",
			"ON_BOARD,COMPLETED,true",
			"ON_BOARD,CANCELLED,true",
			"ON_BOARD,APPROACHING,false",
			"COMPLETED,CANCELLED,false",
			"COMPLETED,ON_BOARD,false",
			"CANCELLED,CONFIRMED,false",
			"CANCELLED,COMPLETED,false"
	})
	void validatesAllowedTransitions(TripStatus from, TripStatus to, boolean allowed) {
		assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
	}

	@ParameterizedTest
	@EnumSource(value = TripStatus.class, names = { "COMPLETED", "CANCELLED" })
	void terminalStatusesHaveNoOutgoingTransitions(TripStatus status) {
		assertThat(status.isTerminal()).isTrue();
		assertThat(status.isActive()).isFalse();
		for (TripStatus next : TripStatus.values()) {
			assertThat(status.canTransitionTo(next)).isFalse();
		}
	}

	@ParameterizedTest
	@EnumSource(value = TripStatus.class, names = { "CONFIRMED", "APPROACHING", "ON_BOARD" })
	void activeStatusesAreNonTerminal(TripStatus status) {
		assertThat(status.isActive()).isTrue();
		assertThat(status.isTerminal()).isFalse();
	}

	@Test
	void rejectsNullTarget() {
		assertThat(TripStatus.CONFIRMED.canTransitionTo(null)).isFalse();
	}

	@Test
	void labelsArePresent() {
		for (TripStatus status : TripStatus.values()) {
			assertThat(status.label()).isNotBlank();
		}
	}
}
