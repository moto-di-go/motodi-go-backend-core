package com.motodigo.core.intercity.dto;

import java.util.List;
import java.util.Map;

public record WideningSuggestion(
		String code,
		String message,
		Map<String, Object> suggestedQuery) {
}
