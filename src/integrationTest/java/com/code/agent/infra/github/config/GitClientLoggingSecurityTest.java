package com.code.agent.infra.github.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GitClientLoggingSecurityTest {

    private MockWebServer mockWebServer;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger nettyLogger;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Setup log capture for Reactor Netty HTTP client
        nettyLogger = (Logger) LoggerFactory.getLogger("reactor.netty.http.client");
        listAppender = new ListAppender<>();
        listAppender.start();
        nettyLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
        if (nettyLogger != null && listAppender != null) {
            nettyLogger.detachAppender(listAppender);
        }
    }

    @Test
    void reactorNettyHttpClient_ShouldNotLogAuthorizationHeader_WhenWiretapEnabled() {
        // Given: Dummy token that should NOT appear in logs
        String dummyToken = "secret-token-MUST-NOT-APPEAR-IN-LOGS";
        GitHubProperties.Client clientConfig = new GitHubProperties.Client(
                Duration.ofSeconds(10),
                Duration.ofSeconds(5)
        );
        GitHubProperties properties = new GitHubProperties(
                mockWebServer.url("/").toString(),
                dummyToken,
                "/test",
                clientConfig
        );

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"message\":\"success\"}"));

        // When: Create WebClient with wiretap enabled (to force HTTP logging)
        HttpClient httpClient = HttpClient.create()
                .followRedirect(true)
                .wiretap(true)  // Enable wire logging for testing
                .responseTimeout(clientConfig.responseTimeout());

        WebClient webClient = WebClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("X-Test-Header", "test-value")
                .filter((request, next) -> {
                    ClientRequest modifiedRequest = ClientRequest.from(request)
                            .header("Authorization", "Bearer " + dummyToken)
                            .build();
                    return next.exchange(modifiedRequest);
                })
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        // Execute HTTP request
        webClient.get()
                .uri("/test")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        // Then: Verify token does NOT appear in captured logs
        List<String> logMessages = listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());

        // Security check: Token should not be logged
        assertThat(logMessages)
                .as("Authorization token should not appear in any log messages")
                .noneSatisfy(message ->
                        assertThat(message).doesNotContain(dummyToken)
                );

        assertThat(logMessages)
                .as("Bearer token should not appear in any log messages")
                .noneSatisfy(message ->
                        assertThat(message).doesNotContain("Bearer " + dummyToken)
                );
    }

    @Test
    void webClientBean_ShouldNotExposeToken_InStringRepresentation() {
        // Given: Dummy configuration
        String dummyToken = "another-secret-token-12345";
        GitHubProperties.Client clientConfig = new GitHubProperties.Client(
                Duration.ofSeconds(10),
                Duration.ofSeconds(5)
        );
        GitHubProperties properties = new GitHubProperties(
                "https://api.github.com",
                dummyToken,
                "/test",
                clientConfig
        );

        // When: Create WebClient using filter approach
        GitClientConfig config = new GitClientConfig();
        WebClient webClient = config.gitHubWebClient(properties);

        // Then: Token should not be in bean's string representation
        String webClientString = webClient.toString();
        assertThat(webClientString)
                .as("Token should not be visible in WebClient bean")
                .doesNotContain(dummyToken);
        assertThat(webClientString)
                .as("Authorization header should not be in default headers")
                .doesNotContain("Authorization");
    }
}
