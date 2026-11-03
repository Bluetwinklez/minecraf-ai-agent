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
			throw new IOException("Provider returned HTTP " + response.statusCode());
		}
		JsonElement json;
		try {
			json = JsonParser.parseString(response.body());
		} catch (com.google.gson.JsonParseException e) {
			// Provider bodies may contain credentials or private prompt content.
			throw new IOException("Provider returned invalid JSON");
		}
		if (!json.isJsonObject()) {
			throw new IOException("Provider returned a non-object JSON response");
		}
		return json.getAsJsonObject();
	}

}
