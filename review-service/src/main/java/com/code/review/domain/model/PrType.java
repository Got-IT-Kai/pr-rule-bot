package com.code.review.domain.model;

import java.util.Arrays;
import java.util.regex.Pattern;

public enum PrType {
    FEATURE(
            "Feature",
            null,
            "^(feat|feature)(\\(.*\\))?:.*"
    ),
    BUGFIX(
            "Bug Fix",
            "Critical bugs, error handling, edge cases",
            "^(fix|bugfix)(\\(.*\\))?:.*",
            ".*\\b(fix|bug)\\b.*"
    ),
    REFACTOR(
            "Refactor",
            "Code structure, maintainability, design patterns",
            "^refactor(\\(.*\\))?:.*",
            ".*\\brefactor\\b.*"
    ),
    DOCS(
            "Documentation",
            "Documentation accuracy, clarity, completeness",
            "^docs?(\\(.*\\))?:.*",
            ".*\\bdoc\\b.*"
    ),
    TEST(
            "Test",
            "Test coverage, test quality, edge cases",
            "^test(\\(.*\\))?:.*",
            ".*\\btest\\b.*"
    ),
    CHORE(
            "Chore",
            null,
            "^chore(\\(.*\\))?:.*"
    ),
    PERFORMANCE(
            "Performance",
            "Performance impact, resource usage, scalability",
            "^perf(\\(.*\\))?:.*",
            ".*\\b(perf|optimize)\\b.*"
    ),
    SECURITY(
            "Security",
            "Security vulnerabilities, data protection, authentication",
            "^(security|sec)(\\(.*\\))?:.*",
            ".*\\b(security|vulnerability)\\b.*"
    ),
    UNKNOWN("Unknown", null);

    private final String displayName;
    private final String focus;
    private final Pattern[] patterns;

    PrType(String displayName, String focus, String... regexPatterns) {
        this.displayName = displayName;
        this.focus = focus;
        this.patterns = Arrays.stream(regexPatterns)
                .map(Pattern::compile)
                .toArray(Pattern[]::new);
    }

    public String displayName() {
        return displayName;
    }

    public String focus() {
        return focus;
    }

    public boolean matches(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        String lowerTitle = title.toLowerCase();
        return Arrays.stream(patterns)
                .anyMatch(pattern -> pattern.matcher(lowerTitle).matches());
    }

    public static PrType detect(String title) {
        if (title == null || title.isBlank()) {
            return UNKNOWN;
        }

        return Arrays.stream(values())
                .filter(type -> type != UNKNOWN && type.matches(title))
                .findFirst()
                .orElse(FEATURE);
    }
}
