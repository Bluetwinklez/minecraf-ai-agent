package com.bluetwinklez.aibuilders.ai;

import java.io.IOException;
import java.util.List;

/** A chat model backend. Calls are blocking and run on the chat worker thread, never the server thread. */
public interface LlmProvider {
	record Message(String role, String content) {
	}

	String reply(String system, List<Message> history) throws IOException, InterruptedException;
}
