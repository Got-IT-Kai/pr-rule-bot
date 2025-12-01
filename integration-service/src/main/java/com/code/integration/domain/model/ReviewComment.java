package com.code.integration.domain.model;

import java.util.List;

public record ReviewComment(
        String repositoryOwner,
        String repositoryName,
        Integer pullRequestNumber,
        String body,
        List<FileLevelComment> fileLevelComments
) {
    public record FileLevelComment(
            String path,
            Integer line,
            String body
    ) {}
}
