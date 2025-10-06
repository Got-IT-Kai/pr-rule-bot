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

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(5);
    private static final String TEST_PATH = "/test";
    private static final String TEST_API_PATH = "/api/test";

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
    void shouldNotLogAuthorizationHeaderWhenWiretapEnabled() {
        // Given: WebClient with wiretap enabled
        String dummyToken = "secret-token-MUST-NOT-APPEAR-IN-LOGS";
        GitHubProperties.Client clientConfig = new GitHubProperties.Client(
                CONNECT_TIMEOUT,
                RESPONSE_TIMEOUT
        );
        GitHubProperties properties = new GitHubProperties(
                mockWebServer.url("/").toString(),
                dummyToken,
                TEST_PATH,
                clientConfig
        );

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"message\":\"success\"}"));

        HttpClient httpClient = HttpClient.create()
                .followRedirect(true)
                .wiretap(true)
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

        // When: Execute HTTP request
        webClient.get()
                .uri(TEST_PATH)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        // Then: Token should not appear in any log message
        List<String> logMessages = listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        // Security check: Token should not be logged
        assertThat(logMessages)
                .as("Authorization token and Bearer prefix should not appear in any log messages")
                .noneSatisfy(message ->
                        assertThat(message)
                                .doesNotContain(dummyToken)
                                .doesNotContain("Bearer " + dummyToken)
                );
    }

    @Test
    void shouldNotExposeTokenInStringRepresentation() {
        // Given: WebClient configured with token
        String dummyToken = "another-secret-token-12345";
        WebClient webClient = createWebClient(dummyToken);

        // When: Get string representation of WebClient
        String webClientString = webClient.toString();

        // Then: Token should not appear in string representation
        assertThat(webClientString)
                .as("Token and Authorization header should not be visible in WebClient bean")
                .doesNotContain(dummyToken)
                .doesNotContain("Authorization");
    }

    private static WebClient createWebClient(String token) {
        GitHubProperties.Client clientConfig = new GitHubProperties.Client(
                CONNECT_TIMEOUT,
                RESPONSE_TIMEOUT
        );
        GitHubProperties properties = new GitHubProperties(
                "https://api.github.com",
                token,
                TEST_PATH,
                clientConfig
        );

        GitClientConfig config = new GitClientConfig();
        return config.gitHubWebClient(properties);
    }

    @Test
    void shouldAddAuthorizationHeaderToActualRequest() throws Exception {
        // Given: WebClient configured with authentication token
        String dummyToken = "test-token-should-be-sent";
        GitHubProperties.Client clientConfig = new GitHubProperties.Client(
                CONNECT_TIMEOUT,
                RESPONSE_TIMEOUT
        );
        GitHubProperties properties = new GitHubProperties(
                mockWebServer.url("/").toString(),
                dummyToken,
                TEST_PATH,
                clientConfig
        );

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"message\":\"success\"}"));

        GitClientConfig config = new GitClientConfig();
        WebClient webClient = config.gitHubWebClient(properties);

        // When: Execute HTTP request
        webClient.get()
                .uri(TEST_API_PATH)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        // Then: Authorization header should be present in the actual HTTP request
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
    void shouldNotExposeVariousTokensInStringRepresentation(String token) {
        // Given: WebClient configured with various token formats
        WebClient webClient = createWebClient(token);

        // When: Get string representation of WebClient
        String webClientString = webClient.toString();

        // Then: Token should not appear in string representation
        assertThat(webClientString)
                .as("Token '%s' should not be visible in WebClient bean", token)
                .doesNotContain(token);
    }

    @Test
    void shouldThrowExceptionWhenTokenIsNull() {
        // Given: WebClient configured with null token
        GitHubProperties.Client clientConfig = new GitHubProperties.Client(
                CONNECT_TIMEOUT,
                RESPONSE_TIMEOUT
        );
        GitHubProperties properties = new GitHubProperties(
                mockWebServer.url("/").toString(),
                null,
                TEST_PATH,
                clientConfig
        );

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        GitClientConfig config = new GitClientConfig();
        WebClient webClient = config.gitHubWebClient(properties);

        // When: Execute HTTP request with null token
        // Then: Should throw IllegalStateException
        assertThatThrownBy(() ->
                webClient.get()
                        .uri(TEST_PATH)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block()
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GitHub token is required");
    }

    @Test
    void shouldThrowExceptionWhenTokenIsBlank() {
        // Given: WebClient configured with blank token
        GitHubProperties.Client clientConfig = new GitHubProperties.Client(
                CONNECT_TIMEOUT,
                RESPONSE_TIMEOUT
        );
        GitHubProperties properties = new GitHubProperties(
                mockWebServer.url("/").toString(),
                "   ",
                TEST_PATH,
                clientConfig
        );

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        GitClientConfig config = new GitClientConfig();
        WebClient webClient = config.gitHubWebClient(properties);

        // When: Execute HTTP request with blank token
        // Then: Should throw IllegalStateException
        assertThatThrownBy(() ->
                webClient.get()
                        .uri(TEST_PATH)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block()
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GitHub token is required");
    }
}
