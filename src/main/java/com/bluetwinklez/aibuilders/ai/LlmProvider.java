package com.bluetwinklez.aibuilders.ai;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;

/** A chat model backend. Calls are blocking and run on the chat worker thread, never the server thread. */
public interface LlmProvider {
	/** A function the model may call; {@code parameters} is a JSON schema object. */
	record ToolSpec(String name, String description, JsonObject parameters) {
	}

	record ToolCall(String id, String name, JsonObject arguments) {
	}

	/**
	 * One conversation entry. Roles: {@code user}, {@code assistant} (may carry tool calls) and
	 * {@code tool} (result of the call with {@code toolCallId}).
	 */
	record Message(String role, String content, List<ToolCall> toolCalls, String toolCallId, String toolName) {
		public Message(String role, String content) {
			this(role, content, List.of(), null, null);
		}

		public static Message assistant(String text, List<ToolCall> calls) {
			return new Message("assistant", text == null ? "" : text, List.copyOf(calls), null, null);
		}

		public static Message toolResult(ToolCall call, String result) {
			return new Message("tool", result, List.of(), call.id(), call.name());
		}
	}

	record Response(String text, List<ToolCall> toolCalls) {
	}

	Response chat(String system, List<Message> history, List<ToolSpec> tools) throws IOException, InterruptedException;

	default String reply(String system, List<Message> history) throws IOException, InterruptedException {
		return chat(system, history, List.of()).text();
	}
}
