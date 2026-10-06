package com.bluetwinklez.aibuilders.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Claude Messages API (paid), with tool use. The API key never leaves the server. */
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
	public Response chat(String system, List<Message> history, List<ToolSpec> tools) throws IOException, InterruptedException {
		if (apiKey.isBlank()) {
			throw new IOException("Claude API key is not set");
		}
		Map<String, String> headers = Map.of("x-api-key", apiKey, "anthropic-version", "2023-06-01");
		return parse(HttpJson.post(URL, headers, body(model, maxTokens, system, history, tools), timeoutSeconds));
	}

	static JsonObject body(String model, int maxTokens, String system, List<Message> history, List<ToolSpec> tools) {
		JsonArray messages = new JsonArray();
		JsonArray pendingResults = null;
		for (Message m : history) {
			if (m.role().equals("tool")) {
				// Consecutive tool results go into one user turn as tool_result blocks.
				if (pendingResults == null) {
					pendingResults = new JsonArray();
				}
				JsonObject result = new JsonObject();
				result.addProperty("type", "tool_result");
				result.addProperty("tool_use_id", m.toolCallId());
				result.addProperty("content", m.content());
				pendingResults.add(result);
				continue;
			}
			if (pendingResults != null) {
				messages.add(userBlocks(pendingResults));
				pendingResults = null;
			}
			JsonObject msg = new JsonObject();
			msg.addProperty("role", m.role());
			if (m.toolCalls().isEmpty()) {
				msg.addProperty("content", m.content());
			} else {
				JsonArray blocks = new JsonArray();
				if (!m.content().isBlank()) {
					JsonObject text = new JsonObject();
					text.addProperty("type", "text");
					text.addProperty("text", m.content());
					blocks.add(text);
				}
				for (ToolCall call : m.toolCalls()) {
					JsonObject use = new JsonObject();
					use.addProperty("type", "tool_use");
					use.addProperty("id", call.id());
					use.addProperty("name", call.name());
					use.add("input", call.arguments());
					blocks.add(use);
				}
				msg.add("content", blocks);
			}
			messages.add(msg);
		}
		if (pendingResults != null) {
			messages.add(userBlocks(pendingResults));
		}
		JsonObject body = new JsonObject();
		body.addProperty("model", model);
		body.addProperty("max_tokens", maxTokens);
		body.addProperty("system", system);
		body.add("messages", messages);
		if (!tools.isEmpty()) {
			JsonArray toolArray = new JsonArray();
			for (ToolSpec t : tools) {
				JsonObject tool = new JsonObject();
				tool.addProperty("name", t.name());
				tool.addProperty("description", t.description());
				tool.add("input_schema", t.parameters());
				toolArray.add(tool);
			}
			body.add("tools", toolArray);
		}
		return body;
	}

	private static JsonObject userBlocks(JsonArray blocks) {
		JsonObject msg = new JsonObject();
		msg.addProperty("role", "user");
		msg.add("content", blocks);
		return msg;
	}

	static Response parse(JsonObject response) throws IOException {
		if (response.has("error")) {
			throw new IOException("Claude: " + response.get("error"));
		}
		JsonArray content = response.getAsJsonArray("content");
		if (content == null) {
			throw new IOException("Claude response has no content");
		}
		StringBuilder text = new StringBuilder();
		List<ToolCall> calls = new ArrayList<>();
		for (JsonElement block : content) {
			JsonObject b = block.getAsJsonObject();
			String type = b.has("type") ? b.get("type").getAsString() : "";
			if (type.equals("text") && b.has("text")) {
				text.append(b.get("text").getAsString());
			} else if (type.equals("tool_use") && b.has("name")) {
				JsonObject input = b.has("input") && b.get("input").isJsonObject() ? b.getAsJsonObject("input") : new JsonObject();
				calls.add(new ToolCall(b.has("id") ? b.get("id").getAsString() : "call_" + calls.size(), b.get("name").getAsString(), input));
			}
		}
		if (text.isEmpty() && calls.isEmpty()) {
			throw new IOException("Claude response has no text");
		}
		return new Response(text.toString().strip(), calls);
	}
}
