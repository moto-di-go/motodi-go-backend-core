package com.motodigo.core.geo.event;

import com.motodigo.core.geo.entity.DriverPositionRecord;

public record DriverPositionUpdatedEvent(DriverPositionRecord position) {
}
