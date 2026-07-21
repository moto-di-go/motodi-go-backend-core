package com.motodigo.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.motodigo.core.auth.config.AfricasTalkingProperties;
import com.motodigo.core.auth.exception.AuthException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AfricasTalkingOtpSenderTest {

	private final BlockingQueue<CapturedSmsRequest> capturedRequests = new LinkedBlockingQueue<>();
	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (this.server != null) {
			this.server.stop(0);
		}
	}

	@Test
	void sendsBulkSmsJsonRequestWithApiKeyHeader() throws Exception {
		startServer(200, """
				{"SMSMessageData":{"Message":"Sent","Recipients":[{"statusCode":101,"number":"+237699001122","status":"Success"}]}}
				""");
		AfricasTalkingOtpSender sender = new AfricasTalkingOtpSender(properties());

		sender.sendOtp("+237699001122", "Votre code est 123456");

		CapturedSmsRequest request = this.capturedRequests.poll(2, TimeUnit.SECONDS);
		assertThat(request.path()).isEqualTo("/version1/messaging/bulk");
		assertThat(request.apiKey()).isEqualTo("test-key");
		assertThat(request.body()).contains("\"username\":\"test-user\"");
		assertThat(request.body()).contains("\"phoneNumbers\":[\"+237699001122\"]");
		assertThat(request.body()).contains("\"senderId\":\"MOTODIGO\"");
		assertThat(request.body()).contains("\"enqueue\":1");
	}

	@Test
	void rejectsNonSuccessfulRecipientStatus() throws Exception {
		startServer(200, """
				{"SMSMessageData":{"Message":"Rejected","Recipients":[{"statusCode":403,"number":"+237699001122","status":"Invalid"}]}}
				""");
		AfricasTalkingOtpSender sender = new AfricasTalkingOtpSender(properties());

		assertThatThrownBy(() -> sender.sendOtp("+237699001122", "Votre code est 123456"))
				.isInstanceOf(AuthException.class);
	}

	private void startServer(int status, String body) throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		this.server.createContext("/", exchange -> {
			String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			this.capturedRequests.add(new CapturedSmsRequest(
					exchange.getRequestURI().getPath(),
					exchange.getRequestHeaders().getFirst("apiKey"),
					requestBody));
			byte[] response = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, response.length);
			try (OutputStream outputStream = exchange.getResponseBody()) {
				outputStream.write(response);
			}
		});
		this.server.start();
	}

	private AfricasTalkingProperties properties() {
		return new AfricasTalkingProperties(
				URI.create("http://localhost:" + this.server.getAddress().getPort()),
				"test-user",
				"test-key",
				"MOTODIGO",
				1,
				1000,
				1000);
	}

	private record CapturedSmsRequest(String path, String apiKey, String body) {
	}
}
