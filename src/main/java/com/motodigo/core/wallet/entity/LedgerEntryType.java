package com.motodigo.core.wallet.entity;

/**
 * Type of accounting movement recorded in {@code ledger_entries}.
 *
 * <p>{@code balanceSign} is the effect of a settled entry on {@code wallets.current_balance}:
 * {@code +1} increases it, {@code -1} decreases it, {@code 0} leaves it untouched. For
 * CORE-WALLET-001 only {@link #CREDIT} and {@link #DEBIT} move the settled balance. The
 * reservation semantics of {@link #HOLD}/{@link #RELEASE} (available vs. current balance) and
 * {@link #REVERSAL} flows are deferred to CORE-NFC-001 / refunds, which reuse the same posting
 * engine; they are declared here so the domain matches the DB {@code chk_ledger_entries_type}
 * constraint but currently carry a {@code 0} balance effect.
 */
public enum LedgerEntryType {
	CREDIT(1),
	DEBIT(-1),
	REVERSAL(0),
	HOLD(0),
	RELEASE(0);

	private final int balanceSign;

	LedgerEntryType(int balanceSign) {
		this.balanceSign = balanceSign;
	}

	public int balanceSign() {
		return this.balanceSign;
	}

	public boolean movesSettledBalance() {
		return this.balanceSign != 0;
	}
}
