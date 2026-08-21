package com.motodigo.core.ops.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminOpsServiceMaskingTest {

	@Test
	void masksPhoneAndEmailUnlessRevealed() {
		assertThat(AdminOpsService.maskPhone("+237699001122", false)).isEqualTo("+237****1122");
		assertThat(AdminOpsService.maskPhone("+237699001122", true)).isEqualTo("+237699001122");
		assertThat(AdminOpsService.maskEmail("support@motodi.go", false)).isEqualTo("s****@motodi.go");
		assertThat(AdminOpsService.maskEmail("support@motodi.go", true)).isEqualTo("support@motodi.go");
	}
}
