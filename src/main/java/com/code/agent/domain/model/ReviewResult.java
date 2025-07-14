package com.code.agent.domain.model;

public record ReviewResult(
        boolean approved,
        String comment
) {}
