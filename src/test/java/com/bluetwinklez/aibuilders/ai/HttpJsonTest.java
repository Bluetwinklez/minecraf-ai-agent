package com.bluetwinklez.aibuilders.ai;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HttpJsonTest {
	private static final String PRIVATE = "PRIVATE_PROVIDER_CONTENT";

	private void checkFailure(int status, String body) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, bytes.length);
			try (var out = exchange.getResponseBody()) { out.write(bytes); }
		});
		server.start();
		try {
			IOException error = assertThrows(IOException.class, () -> HttpJson.post(
				"http://127.0.0.1:" + server.getAddress().getPort() + "/", Map.of(), new JsonObject(), 3));
			assertFalse(error.toString().contains(PRIVATE));
			assertNull(error.getCause(), "Raw parser/provider errors must not leak through a cause");
		} finally { server.stop(0); }
	}

	@Test void httpErrorsDoNotExposeProviderBody() throws Exception { checkFailure(401, PRIVATE); }
	@Test void invalidJsonDoesNotExposeProviderBody() throws Exception { checkFailure(200, "[" + PRIVATE); }
	@Test void nonObjectJsonDoesNotExposeProviderBody() throws Exception { checkFailure(200, "[\"" + PRIVATE + "\"]"); }
}
