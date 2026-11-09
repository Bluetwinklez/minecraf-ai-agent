package com.bluetwinklez.aibuilders.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

final class HttpJson {
	private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	private HttpJson() {
	}

	static JsonObject post(String url, Map<String, String> headers, JsonObject body, int timeoutSeconds) throws IOException, InterruptedException {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(timeoutSeconds))
			.header("content-type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
		headers.forEach(request::header);
		HttpResponse<String> response = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() / 100 != 2) {
			throw new IOException("HTTP " + response.statusCode() + ": " + truncate(response.body()));
		}
		JsonElement json = JsonParser.parseString(response.body());
		if (!json.isJsonObject()) {
			throw new IOException("Unexpected response: " + truncate(response.body()));
		}
		return json.getAsJsonObject();
	}

	private static String truncate(String s) {
		return s.length() > 300 ? s.substring(0, 300) + "..." : s;
	}
}
