package com.swiftkart.knowledge.dto;

import java.util.Map;

public record DocumentResponse(String id, String content, Map<String, Object> metadata) {
}
