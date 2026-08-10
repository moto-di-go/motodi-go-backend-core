package com.motodigo.core.intercity.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccentFoldTest {

	@Test
	void foldsCaseAndFrenchAccents() {
		assertThat(AccentFold.fold("Yaoundé")).isEqualTo("yaounde");
		assertThat(AccentFold.fold("Yaounde")).isEqualTo("yaounde");
		assertThat(AccentFold.fold("YAOUNDÉ")).isEqualTo("yaounde");
		assertThat(AccentFold.fold("  Douala  ")).isEqualTo("douala");
		assertThat(AccentFold.fold("")).isNull();
		assertThat(AccentFold.fold(null)).isNull();
	}
}
