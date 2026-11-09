package com.bluetwinklez.aibuilders.ai;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Runs model turns until it answers in text, executing requested tools in between. */
public final class AgentLoop {
	/** Executes one tool call and returns the text result handed back to the model. */
	@FunctionalInterface
	public interface ToolExecutor {
		ToolOutcome execute(LlmProvider.ToolCall call);
	}

	/** {@code chatNote} is shown to players when the tool changed something (null otherwise). */
	public record ToolOutcome(String forModel, String chatNote) {
	}

	public record Result(String text, List<String> notes) {
	}

	private AgentLoop() {
	}

	public static Result run(LlmProvider provider, String system, List<LlmProvider.Message> history, List<LlmProvider.ToolSpec> tools,
		ToolExecutor executor, int maxRounds) throws IOException, InterruptedException {
		List<LlmProvider.Message> messages = new ArrayList<>(history);
		List<String> notes = new ArrayList<>();
		for (int round = 0; round <= maxRounds; round++) {
			// On the last round the model must answer in text.
			List<LlmProvider.ToolSpec> offered = round == maxRounds ? List.of() : tools;
			LlmProvider.Response response = provider.chat(system, messages, offered);
			if (response.toolCalls().isEmpty() || offered.isEmpty()) {
				return new Result(response.text(), notes);
			}
			messages.add(LlmProvider.Message.assistant(response.text(), response.toolCalls()));
			for (LlmProvider.ToolCall call : response.toolCalls()) {
				ToolOutcome outcome = executor.execute(call);
				messages.add(LlmProvider.Message.toolResult(call, outcome.forModel()));
				if (outcome.chatNote() != null) {
					notes.add(outcome.chatNote());
				}
			}
		}
		return new Result("", notes);
	}
}
