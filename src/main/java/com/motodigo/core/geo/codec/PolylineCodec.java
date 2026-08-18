package com.motodigo.core.geo.codec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PolylineCodec {

	private PolylineCodec() {
	}

	public static List<double[]> decode(String encoded, int precision) {
		List<double[]> coords = new ArrayList<>();
		if (encoded == null || encoded.isBlank()) {
			return coords;
		}
		double factor = Math.pow(10, precision);
		int index = 0;
		int lat = 0;
		int lon = 0;
		while (index < encoded.length()) {
			int[] latResult = next(encoded, index);
			index = latResult[1];
			lat += latResult[0];
			int[] lonResult = next(encoded, index);
			index = lonResult[1];
			lon += lonResult[0];
			coords.add(new double[] {lat / factor, lon / factor});
		}
		return coords;
	}

	public static String toLineStringWkt(List<double[]> latLon) {
		if (latLon == null || latLon.size() < 2) {
			return "LINESTRING EMPTY";
		}
		StringBuilder builder = new StringBuilder("LINESTRING(");
		for (int i = 0; i < latLon.size(); i++) {
			if (i > 0) {
				builder.append(", ");
			}
			builder.append(String.format(Locale.US, "%f %f", latLon.get(i)[1], latLon.get(i)[0]));
		}
		builder.append(')');
		return builder.toString();
	}

	private static int[] next(String encoded, int index) {
		int result = 0;
		int shift = 0;
		int b;
		do {
			b = encoded.charAt(index++) - 63;
			result |= (b & 0x1f) << shift;
			shift += 5;
		}
		while (b >= 0x20 && index < encoded.length());
		int delta = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
		return new int[] {delta, index};
	}
}
