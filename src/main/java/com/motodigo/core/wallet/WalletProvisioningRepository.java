package com.motodigo.core.wallet;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class WalletProvisioningRepository {

	private final JdbcTemplate jdbcTemplate;

	public WalletProvisioningRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void createActiveWalletIfMissing(UUID userId) {
		this.jdbcTemplate.update("""
				INSERT INTO wallets (user_id, currency, current_balance, status)
				VALUES (?, 'XAF', 0, 'ACTIVE')
				ON CONFLICT (user_id) DO NOTHING
				""", userId);
	}
}
