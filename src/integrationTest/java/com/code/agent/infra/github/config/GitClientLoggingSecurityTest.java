package com.code.agent.infra.github.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void gitHubWebClient_ShouldAddAuthorizationHeaderToActualRequest() throws Exception {
        // Given: Dummy token for testing
        String dummyToken = "test-token-should-be-sent";
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

        // When: Create WebClient and make actual HTTP request
        GitClientConfig config = new GitClientConfig();
        WebClient webClient = config.gitHubWebClient(properties);

        webClient.get()
                .uri("/api/test")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        // Then: Verify Authorization header was added to the actual HTTP request
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recordedRequest).isNotNull();
        assertThat(recordedRequest.getHeader("Authorization"))
                .as("Authorization header should be present in HTTP request")
                .isEqualTo("Bearer " + dummyToken);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "secret-token-123",
            "another-token-with-special-chars-!@#$%",
            "very-long-token-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    })
    void webClientBean_ShouldNotExposeVariousTokens_InStringRepresentation(String dummyToken) {
        // Given: Various dummy tokens
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
                .as("Token '%s' should not be visible in WebClient bean", dummyToken)
                .doesNotContain(dummyToken);
    }

    @Test
    void gitHubWebClient_ShouldThrowException_WhenTokenIsNull() throws Exception {
        // Given: Null token
        GitHubProperties.Client clientConfig = new GitHubProperties.Client(
                Duration.ofSeconds(10),
                Duration.ofSeconds(5)
        );
        GitHubProperties properties = new GitHubProperties(
                mockWebServer.url("/").toString(),
                null,
                "/test",
                clientConfig
        );

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        // When: Create WebClient and attempt to make request
        GitClientConfig config = new GitClientConfig();
        WebClient webClient = config.gitHubWebClient(properties);

        // Then: Should throw IllegalStateException when filter is executed
        assertThatThrownBy(() ->
                webClient.get()
                        .uri("/test")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block()
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GitHub token is required");
    }

    @Test
    void gitHubWebClient_ShouldThrowException_WhenTokenIsBlank() throws Exception {
        // Given: Blank token
        GitHubProperties.Client clientConfig = new GitHubProperties.Client(
                Duration.ofSeconds(10),
                Duration.ofSeconds(5)
        );
        GitHubProperties properties = new GitHubProperties(
                mockWebServer.url("/").toString(),
                "   ",
                "/test",
                clientConfig
        );

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        // When: Create WebClient and attempt to make request
        GitClientConfig config = new GitClientConfig();
        WebClient webClient = config.gitHubWebClient(properties);

        // Then: Should throw IllegalStateException when filter is executed
        assertThatThrownBy(() ->
                webClient.get()
                        .uri("/test")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block()
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GitHub token is required");
    }
}
