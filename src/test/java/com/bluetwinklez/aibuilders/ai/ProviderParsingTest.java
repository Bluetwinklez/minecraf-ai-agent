package com.bluetwinklez.aibuilders.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderParsingTest {
	private static final List<LlmProvider.ToolSpec> TOOLS = List.of(new LlmProvider.ToolSpec("stop", "Stop",
		json("{\"type\":\"object\",\"properties\":{\"npc\":{\"type\":\"string\"}}}")));

	private static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	@Test
	void ollamaRequestHasSystemFirstAndNoStreaming() {
		JsonObject body = OllamaProvider.body("qwen2.5:3b", 200, "sys", List.of(new LlmProvider.Message("user", "hi")), List.of());
		assertEquals("qwen2.5:3b", body.get("model").getAsString());
		assertFalse(body.get("stream").getAsBoolean());
		assertEquals("system", body.getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString());
		assertEquals("hi", body.getAsJsonArray("messages").get(1).getAsJsonObject().get("content").getAsString());
		assertEquals(200, body.getAsJsonObject("options").get("num_predict").getAsInt());
		assertFalse(body.has("tools"));
	}

	@Test
	void ollamaResponseParsing() throws IOException {
		assertEquals("as!", OllamaProvider.parse(json("{\"message\":{\"role\":\"assistant\",\"content\":\" as! \"},\"done\":true}")).text());
		assertThrows(IOException.class, () -> OllamaProvider.parse(json("{\"error\":\"model 'x' not found\"}")));
		assertThrows(IOException.class, () -> OllamaProvider.parse(json("{\"done\":true}")));
		assertThrows(IOException.class, () -> OllamaProvider.parse(json("{\"message\":{\"content\":\"\"}}")));
	}

	@Test
	void ollamaToolsRequestAndToolCallParsing() throws IOException {
		LlmProvider.ToolCall call = new LlmProvider.ToolCall("call_0", "stop", json("{\"npc\":\"Ali\"}"));
		List<LlmProvider.Message> history = List.of(
			new LlmProvider.Message("user", "Ali'yi durdur"),
			LlmProvider.Message.assistant("", List.of(call)),
			LlmProvider.Message.toolResult(call, "OK"));
		JsonObject body = OllamaProvider.body("m", 10, "sys", history, TOOLS);
		JsonObject tool = body.getAsJsonArray("tools").get(0).getAsJsonObject();
		assertEquals("function", tool.get("type").getAsString());
		assertEquals("stop", tool.getAsJsonObject("function").get("name").getAsString());
		JsonObject assistant = body.getAsJsonArray("messages").get(2).getAsJsonObject();
		assertEquals("Ali", assistant.getAsJsonArray("tool_calls").get(0).getAsJsonObject()
			.getAsJsonObject("function").getAsJsonObject("arguments").get("npc").getAsString());
		JsonObject result = body.getAsJsonArray("messages").get(3).getAsJsonObject();
		assertEquals("tool", result.get("role").getAsString());
		assertEquals("stop", result.get("tool_name").getAsString());

		LlmProvider.Response r = OllamaProvider.parse(json(
			"{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"function\":{\"name\":\"stop\",\"arguments\":{\"npc\":\"Ali\"}}}]}}"));
		assertEquals("stop", r.toolCalls().getFirst().name());
		assertEquals("Ali", r.toolCalls().getFirst().arguments().get("npc").getAsString());
		// Small models sometimes send arguments as a JSON string.
		LlmProvider.Response s = OllamaProvider.parse(json(
			"{\"message\":{\"content\":\"\",\"tool_calls\":[{\"function\":{\"name\":\"stop\",\"arguments\":\"{\\\"npc\\\":\\\"Veli\\\"}\"}}]}}"));
		assertEquals("Veli", s.toolCalls().getFirst().arguments().get("npc").getAsString());
	}

	@Test
	void claudeRequestUsesTopLevelSystem() {
		JsonObject body = ClaudeProvider.body("claude-sonnet-5-5", 100, "sys", List.of(new LlmProvider.Message("user", "hi")), List.of());
		assertEquals("sys", body.get("system").getAsString());
		assertEquals(100, body.get("max_tokens").getAsInt());
		assertEquals(1, body.getAsJsonArray("messages").size());
		assertFalse(body.has("tools"));
	}

	@Test
	void claudeResponseJoinsTextBlocks() throws IOException {
		assertEquals("Test başarılı.", ClaudeProvider.parse(json(
			"{\"content\":[{\"type\":\"text\",\"text\":\"Test \"},{\"type\":\"text\",\"text\":\"başarılı.\"}],\"stop_reason\":\"end_turn\"}")).text());
		assertThrows(IOException.class, () -> ClaudeProvider.parse(json("{\"content\":[]}")));
		assertThrows(IOException.class, () -> ClaudeProvider.parse(json("{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\"}}")));
	}

	@Test
	void claudeToolUseRoundTrip() throws IOException {
		LlmProvider.Response r = ClaudeProvider.parse(json(
			"{\"content\":[{\"type\":\"text\",\"text\":\"Durduruyorum.\"},"
				+ "{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"stop\",\"input\":{\"npc\":\"Ali\"}}],\"stop_reason\":\"tool_use\"}"));
		assertEquals("Durduruyorum.", r.text());
		LlmProvider.ToolCall call = r.toolCalls().getFirst();
		assertEquals("toolu_1", call.id());
		assertEquals("Ali", call.arguments().get("npc").getAsString());

		List<LlmProvider.Message> history = List.of(
			new LlmProvider.Message("user", "stop Ali"),
			LlmProvider.Message.assistant(r.text(), r.toolCalls()),
			LlmProvider.Message.toolResult(call, "OK: stopped"));
		JsonObject body = ClaudeProvider.body("m", 10, "sys", history, TOOLS);
		JsonObject tool = body.getAsJsonArray("tools").get(0).getAsJsonObject();
		assertEquals("stop", tool.get("name").getAsString());
		assertTrue(tool.has("input_schema"));
		JsonObject assistant = body.getAsJsonArray("messages").get(1).getAsJsonObject();
		assertEquals("text", assistant.getAsJsonArray("content").get(0).getAsJsonObject().get("type").getAsString());
		assertEquals("tool_use", assistant.getAsJsonArray("content").get(1).getAsJsonObject().get("type").getAsString());
		JsonObject results = body.getAsJsonArray("messages").get(2).getAsJsonObject();
		assertEquals("user", results.get("role").getAsString());
		JsonObject result = results.getAsJsonArray("content").get(0).getAsJsonObject();
		assertEquals("tool_result", result.get("type").getAsString());
		assertEquals("toolu_1", result.get("tool_use_id").getAsString());
	}
}
