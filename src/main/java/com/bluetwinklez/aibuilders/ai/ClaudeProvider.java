package com.bluetwinklez.aibuilders.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Claude Messages API (paid). The API key never leaves the server. */
public final class ClaudeProvider implements LlmProvider {
	private static final String URL = "https://api.anthropic.com/v1/messages";
	private final String apiKey;
	private final String model;
	private final int maxTokens;
	private final int timeoutSeconds;

	public ClaudeProvider(String apiKey, String model, int maxTokens, int timeoutSeconds) {
		this.apiKey = apiKey;
		this.model = model;
		this.maxTokens = maxTokens;
		this.timeoutSeconds = timeoutSeconds;
	}

	@Override
	public String reply(String system, List<Message> history) throws IOException, InterruptedException {
		if (apiKey.isBlank()) {
			throw new IOException("Claude API key is not set");
		}
		Map<String, String> headers = Map.of("x-api-key", apiKey, "anthropic-version", "2023-06-01");
		return parse(HttpJson.post(URL, headers, body(model, maxTokens, system, history), timeoutSeconds));
	}

	static JsonObject body(String model, int maxTokens, String system, List<Message> history) {
		JsonArray messages = new JsonArray();
		for (Message m : history) {
			JsonObject msg = new JsonObject();
			msg.addProperty("role", m.role());
			msg.addProperty("content", m.content());
			messages.add(msg);
		}
		JsonObject body = new JsonObject();
		body.addProperty("model", model);
		body.addProperty("max_tokens", maxTokens);
		body.addProperty("system", system);
		body.add("messages", messages);
		return body;
	}

	static String parse(JsonObject response) throws IOException {
		if (response.has("error")) {
			throw new IOException("Claude: " + response.get("error"));
		}
		JsonArray content = response.getAsJsonArray("content");
		if (content == null) {
			throw new IOException("Claude response has no content");
		}
		StringBuilder text = new StringBuilder();
		for (JsonElement block : content) {
			JsonObject b = block.getAsJsonObject();
			if ("text".equals(b.has("type") ? b.get("type").getAsString() : "") && b.has("text")) {
				text.append(b.get("text").getAsString());
			}
		}
		if (text.isEmpty()) {
			throw new IOException("Claude response has no text");
		}
		return text.toString().strip();
	}
}
