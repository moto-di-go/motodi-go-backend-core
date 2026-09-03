package com.motodigo.core.payment.service;

import java.util.List;
import java.util.UUID;

import com.motodigo.core.payment.dto.ReceiptPageResponse;
import com.motodigo.core.payment.dto.ReceiptResponse;
import com.motodigo.core.payment.entity.ReceiptRecord;
import com.motodigo.core.payment.exception.PaymentErrorCode;
import com.motodigo.core.payment.exception.PaymentException;
import com.motodigo.core.payment.repository.ReceiptRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class ReceiptService {

	private final ReceiptRepository receiptRepository;

	public ReceiptService(ReceiptRepository receiptRepository) {
		this.receiptRepository = receiptRepository;
	}

	@Transactional(readOnly = true)
	public ReceiptPageResponse listForUser(UUID userId, int page, int size) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.max(1, Math.min(size, 100));
		List<ReceiptRecord> rows = this.receiptRepository.listForUser(userId, safeSize + 1, safePage * safeSize);
		boolean hasMore = rows.size() > safeSize;
		List<ReceiptResponse> receipts = rows.stream().limit(safeSize).map(ReceiptResponse::from).toList();
		return new ReceiptPageResponse(receipts, safePage, safeSize, hasMore);
	}

	@Transactional(readOnly = true)
	public ReceiptResponse getForUser(UUID userId, UUID receiptId) {
		return this.receiptRepository.findByIdForUser(receiptId, userId)
				.map(ReceiptResponse::from)
				.orElseThrow(() -> new PaymentException(PaymentErrorCode.RECEIPT_NOT_FOUND));
	}
}
