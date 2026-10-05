package com.bluetwinklez.aibuilders.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderParsingTest {
	private static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	@Test
	void ollamaRequestHasSystemFirstAndNoStreaming() {
		JsonObject body = OllamaProvider.body("qwen2.5:3b", 200, "sys", List.of(new LlmProvider.Message("user", "hi")));
		assertEquals("qwen2.5:3b", body.get("model").getAsString());
		assertFalse(body.get("stream").getAsBoolean());
		assertEquals("system", body.getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString());
		assertEquals("hi", body.getAsJsonArray("messages").get(1).getAsJsonObject().get("content").getAsString());
		assertEquals(200, body.getAsJsonObject("options").get("num_predict").getAsInt());
	}

	@Test
	void ollamaResponseParsing() throws IOException {
		assertEquals("as!", OllamaProvider.parse(json("{\"message\":{\"role\":\"assistant\",\"content\":\" as! \"},\"done\":true}")));
		assertThrows(IOException.class, () -> OllamaProvider.parse(json("{\"error\":\"model 'x' not found\"}")));
		assertThrows(IOException.class, () -> OllamaProvider.parse(json("{\"done\":true}")));
	}

	@Test
	void claudeRequestUsesTopLevelSystem() {
		JsonObject body = ClaudeProvider.body("claude-sonnet-5-5", 100, "sys", List.of(new LlmProvider.Message("user", "hi")));
		assertEquals("sys", body.get("system").getAsString());
		assertEquals(100, body.get("max_tokens").getAsInt());
		assertEquals(1, body.getAsJsonArray("messages").size());
	}

	@Test
	void claudeResponseJoinsTextBlocks() throws IOException {
		assertEquals("Test başarılı.", ClaudeProvider.parse(json(
			"{\"content\":[{\"type\":\"text\",\"text\":\"Test \"},{\"type\":\"text\",\"text\":\"başarılı.\"}],\"stop_reason\":\"end_turn\"}")));
		assertThrows(IOException.class, () -> ClaudeProvider.parse(json("{\"content\":[]}")));
		assertThrows(IOException.class, () -> ClaudeProvider.parse(json("{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\"}}")));
	}
}
