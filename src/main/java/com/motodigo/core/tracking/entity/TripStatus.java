package com.motodigo.core.tracking.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Operational trip status with allowed FSM transitions (CORE-TRACK-001).
 */
public enum TripStatus {
	CONFIRMED,
	APPROACHING,
	ON_BOARD,
	COMPLETED,
	CANCELLED;

	private static final Map<TripStatus, Set<TripStatus>> TRANSITIONS = Map.of(
			CONFIRMED, EnumSet.of(APPROACHING, CANCELLED),
			APPROACHING, EnumSet.of(ON_BOARD, CANCELLED),
			ON_BOARD, EnumSet.of(COMPLETED, CANCELLED),
			COMPLETED, EnumSet.noneOf(TripStatus.class),
			CANCELLED, EnumSet.noneOf(TripStatus.class));

	public boolean canTransitionTo(TripStatus next) {
		if (next == null) {
			return false;
		}
		return TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
	}

	public boolean isTerminal() {
		return this == COMPLETED || this == CANCELLED;
	}

	public boolean isActive() {
		return !isTerminal();
	}

	public String label() {
		return switch (this) {
			case CONFIRMED -> "Trajet confirme";
			case APPROACHING -> "Conducteur en approche";
			case ON_BOARD -> "A bord";
			case COMPLETED -> "Trajet termine";
			case CANCELLED -> "Trajet annule";
		};
	}
}
