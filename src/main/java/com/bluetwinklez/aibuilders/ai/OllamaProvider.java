package com.bluetwinklez.aibuilders.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Free local model served by Ollama ({@code POST /api/chat}), with tool calling. */
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
	public Response chat(String system, List<Message> history, List<ToolSpec> tools) throws IOException, InterruptedException {
		return parse(HttpJson.post(baseUrl + "/api/chat", Map.of(), body(model, maxTokens, system, history, tools), timeoutSeconds));
	}

	static JsonObject body(String model, int maxTokens, String system, List<Message> history, List<ToolSpec> tools) {
		JsonArray messages = new JsonArray();
		messages.add(message("system", system));
		for (Message m : history) {
			JsonObject msg = message(m.role(), m.content());
			if (!m.toolCalls().isEmpty()) {
				JsonArray calls = new JsonArray();
				for (ToolCall call : m.toolCalls()) {
					JsonObject fn = new JsonObject();
					fn.addProperty("name", call.name());
					fn.add("arguments", call.arguments());
					JsonObject c = new JsonObject();
					c.add("function", fn);
					calls.add(c);
				}
				msg.add("tool_calls", calls);
			}
			if (m.toolName() != null) {
				msg.addProperty("tool_name", m.toolName());
			}
			messages.add(msg);
		}
		JsonObject options = new JsonObject();
		options.addProperty("num_predict", maxTokens);
		JsonObject body = new JsonObject();
		body.addProperty("model", model);
		body.add("messages", messages);
		body.addProperty("stream", false);
		body.add("options", options);
		if (!tools.isEmpty()) {
			JsonArray toolArray = new JsonArray();
			for (ToolSpec t : tools) {
				JsonObject fn = new JsonObject();
				fn.addProperty("name", t.name());
				fn.addProperty("description", t.description());
				fn.add("parameters", t.parameters());
				JsonObject tool = new JsonObject();
				tool.addProperty("type", "function");
				tool.add("function", fn);
				toolArray.add(tool);
			}
			body.add("tools", toolArray);
		}
		return body;
	}

	static Response parse(JsonObject response) throws IOException {
		if (response.has("error")) {
			throw new IOException("Ollama: " + response.get("error").getAsString());
		}
		JsonObject message = response.getAsJsonObject("message");
		if (message == null) {
			throw new IOException("Ollama response has no message");
		}
		String text = message.has("content") && !message.get("content").isJsonNull() ? message.get("content").getAsString().strip() : "";
		List<ToolCall> calls = new ArrayList<>();
		if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
			int n = 0;
			for (JsonElement e : message.getAsJsonArray("tool_calls")) {
				JsonObject fn = e.getAsJsonObject().getAsJsonObject("function");
				if (fn == null || !fn.has("name")) {
					continue;
				}
				calls.add(new ToolCall("call_" + n++, fn.get("name").getAsString(), arguments(fn.get("arguments"))));
			}
		}
		if (text.isEmpty() && calls.isEmpty()) {
			throw new IOException("Ollama response is empty");
		}
		return new Response(text, calls);
	}

	/** Small models sometimes send the arguments as a JSON string instead of an object. */
	static JsonObject arguments(JsonElement raw) {
		if (raw == null || raw.isJsonNull()) {
			return new JsonObject();
		}
		if (raw.isJsonObject()) {
			return raw.getAsJsonObject();
		}
		try {
			JsonElement parsed = JsonParser.parseString(raw.getAsString());
			return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
		} catch (RuntimeException e) {
			return new JsonObject();
		}
	}

	private static JsonObject message(String role, String content) {
		JsonObject m = new JsonObject();
		m.addProperty("role", role);
		m.addProperty("content", content);
		return m;
	}
}
