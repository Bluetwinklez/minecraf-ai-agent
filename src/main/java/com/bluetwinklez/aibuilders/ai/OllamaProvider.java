package com.bluetwinklez.aibuilders.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Free local model served by Ollama ({@code POST /api/chat}). */
public final class OllamaProvider implements LlmProvider {
	private final String baseUrl;
	private final String model;
	private final int maxTokens;
	private final int timeoutSeconds;

	public OllamaProvider(String baseUrl, String model, int maxTokens, int timeoutSeconds) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.model = model;
		this.maxTokens = maxTokens;
		this.timeoutSeconds = timeoutSeconds;
	}

	@Override
	public String reply(String system, List<Message> history) throws IOException, InterruptedException {
		return parse(HttpJson.post(baseUrl + "/api/chat", Map.of(), body(model, maxTokens, system, history), timeoutSeconds));
	}

	static JsonObject body(String model, int maxTokens, String system, List<Message> history) {
		JsonArray messages = new JsonArray();
		messages.add(message("system", system));
		for (Message m : history) {
			messages.add(message(m.role(), m.content()));
		}
		JsonObject options = new JsonObject();
		options.addProperty("num_predict", maxTokens);
		JsonObject body = new JsonObject();
		body.addProperty("model", model);
		body.add("messages", messages);
		body.addProperty("stream", false);
		body.add("options", options);
		return body;
	}

	static String parse(JsonObject response) throws IOException {
		if (response.has("error")) {
			throw new IOException("Ollama: " + response.get("error").getAsString());
		}
		JsonObject message = response.getAsJsonObject("message");
		if (message == null || !message.has("content")) {
			throw new IOException("Ollama response has no message");
		}
		return message.get("content").getAsString().strip();
	}

	private static JsonObject message(String role, String content) {
		JsonObject m = new JsonObject();
		m.addProperty("role", role);
		m.addProperty("content", content);
		return m;
	}
}
