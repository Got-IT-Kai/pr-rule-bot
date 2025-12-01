package com.code.context.application.port.outbound;

import reactor.core.publisher.Mono;

public interface GitHubClient {

    Mono<String> getDiff(String diffUrl);

    Mono<String> getFileMetadata(String repositoryOwner, String repositoryName, Integer prNumber);
}
