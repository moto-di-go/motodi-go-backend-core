package com.motodigo.core.kyc.dto;

import com.motodigo.core.kyc.entity.OfficialDocumentRecord;

public record DownloadedKycDocument(
		OfficialDocumentRecord metadata,
		byte[] content) {
}
