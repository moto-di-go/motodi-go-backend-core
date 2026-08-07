-- CORE-WALLET-001: ensure every ACTIVE account has a unique wallet (account-scoped, not profile).
INSERT INTO wallets (user_id, currency, current_balance, status)
SELECT u.id, 'XAF', 0, 'ACTIVE'
FROM users u
WHERE u.status = 'ACTIVE'
ON CONFLICT (user_id) DO NOTHING;
