package com.swiftkart.tools;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class WebSearchTool {

	private static final Logger logger = LoggerFactory.getLogger(WebSearchTool.class);
	private final String TAVILY_SEARCH_URL;

	private final RestClient restClient;
	private final String apiKey;

	public WebSearchTool(RestClient.Builder restClientBuilder,
			@Value("${swiftkart.websearch.api-key:${TAVILY_API_KEY:}}") String apiKey,
			@Value("${swiftkart.websearch.search-url:https://api.tavily.com/search}") String tavilySearchUrl) {
		this.restClient = restClientBuilder.build();
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.TAVILY_SEARCH_URL = tavilySearchUrl;
		logger.info("Tavily API key status: {}", maskApiKey(this.apiKey));
	}

	private String maskApiKey(String value) {
		if (value == null || value.isBlank()) {
			return "blank";
		}
		return value.substring(0, Math.min(4, value.length())) + "****";
	}

	@Tool(description = "Search the web for fresh information such as current events, real-time facts, currency rates, product availability, or delivery updates. Use this when the answer may have changed recently or is not in the conversation context.")
	public String search(String query) {
		logger.info("Web search tool invoked for query: {}", query);

		if (apiKey.isBlank()) {
			logger.warn("Web search skipped because TAVILY_API_KEY is not configured");
			return "Web search is unavailable because the Tavily API key is not configured.";
		}
		if (query == null || query.isBlank()) {
			return "Web search requires a non-blank query.";
		}

		try {
			TavilyResponse response = restClient.post()
					.uri(TAVILY_SEARCH_URL)
					.header("Authorization", "Bearer " + apiKey)
					.body(new TavilyRequest(query, 3))
					.retrieve()
					.body(TavilyResponse.class);

			return formatResults(response);
		} catch (RestClientException exception) {
			logger.error("Tavily web search failed for query: {}", query, exception);
			return "Web search is temporarily unavailable.";
		}
	}

	private String formatResults(TavilyResponse response) {
		if (response == null || response.results() == null || response.results().isEmpty()) {
			return "No web search results were found.";
		}

		return response.results().stream()
				.filter(result -> result != null && result.title() != null && result.content() != null)
				.limit(3)
				.map(result -> "- " + result.title() + ": " + result.content().replaceAll("\\s+", " ").trim())
				.reduce((first, second) -> first + "\n" + second)
				.orElse("No web search results were found.");
	}

	public record TavilyRequest(String query, int max_results) {
	}

	public record TavilyResponse(List<TavilyResult> results) {
	}

	public record TavilyResult(String title, String content) {
	}
}