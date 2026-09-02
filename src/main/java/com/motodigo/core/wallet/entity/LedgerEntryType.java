package com.motodigo.core.wallet.entity;

/**
 * Type of accounting movement recorded in {@code ledger_entries}.
 *
 * <p>{@code balanceSign} is the effect of a settled entry on {@code wallets.current_balance}:
 * {@code +1} increases it, {@code -1} decreases it, {@code 0} leaves it untouched.
 * {@link #HOLD}/{@link #RELEASE} adjust {@code wallets.held_balance} via {@code WalletService}
 * (CORE-NFC-001) without moving the settled balance.
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
