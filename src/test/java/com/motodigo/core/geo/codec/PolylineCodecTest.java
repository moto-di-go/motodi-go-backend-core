package com.motodigo.core.geo.codec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class PolylineCodecTest {

	@Test
	void decodesPrecision5GoogleSample() {
		List<double[]> points = PolylineCodec.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5);

		assertThat(points).hasSize(3);
		assertThat(points.getFirst()[0]).isCloseTo(38.5, org.assertj.core.data.Offset.offset(0.001));
		assertThat(points.getFirst()[1]).isCloseTo(-120.2, org.assertj.core.data.Offset.offset(0.001));
		assertThat(PolylineCodec.toLineStringWkt(points)).startsWith("LINESTRING(");
		assertThat(PolylineCodec.toLineStringWkt(points)).contains("-120.2");
	}

	@Test
	void emptyEncodedYieldsEmptyWkt() {
		assertThat(PolylineCodec.decode("", 6)).isEmpty();
		assertThat(PolylineCodec.toLineStringWkt(List.of())).isEqualTo("LINESTRING EMPTY");
	}
}
