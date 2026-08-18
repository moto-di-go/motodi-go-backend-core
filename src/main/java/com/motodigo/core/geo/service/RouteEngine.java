package com.motodigo.core.geo.service;

import com.motodigo.core.geo.entity.BuiltRoute;

public interface RouteEngine {

	BuiltRoute buildRoute(double originLat, double originLon, double destLat, double destLon);
}
