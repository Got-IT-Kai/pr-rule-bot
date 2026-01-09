package com.code.integration.infrastructure.adapter.outbound.github;

import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GitHubCommentBodyTrimmer {

    private static final int MAX_COMMENT_LENGTH = 60_000;

    private static final String TRUNCATED_NOTICE =
            "\n\n---\nThis review was truncated. See logs for full content.";

    private static final int NOTICE_LEN = TRUNCATED_NOTICE.length();
    private static final int CONTENT_LIMIT = MAX_COMMENT_LENGTH - NOTICE_LEN;

    private static final Pattern INFO_BLOCK = Pattern.compile(
            "<!--SECTION:INFO:START-->.*?<!--SECTION:INFO:END-->", Pattern.DOTALL);
    private static final Pattern MINOR_BLOCK = Pattern.compile(
            "<!--SECTION:MINOR:START-->.*?<!--SECTION:MINOR:END-->", Pattern.DOTALL);
    private static final Pattern MAJOR_BLOCK = Pattern.compile(
            "<!--SECTION:MAJOR:START-->.*?<!--SECTION:MAJOR:END-->", Pattern.DOTALL);

    private static final Rule[] RULES = {
            new Rule(Section.INFO, INFO_BLOCK),
            new Rule(Section.MINOR, MINOR_BLOCK),
            new Rule(Section.MAJOR, MAJOR_BLOCK)
    };

    public String trim(String comment) {
        return trimWithMetadata(comment).body();
    }

    TrimResult trimWithMetadata(String comment) {
        if (comment == null || comment.isEmpty()) {
            return new TrimResult("", false, false, Set.of(), 0, 0);
        }

        int originalLength = comment.length();
        if (originalLength <= MAX_COMMENT_LENGTH) {
            return new TrimResult(comment, false, false, Set.of(), originalLength, originalLength);
        }

        String result = comment;
        EnumSet<Section> removed = EnumSet.noneOf(Section.class);

        for (Rule rule : RULES) {
            if (result.length() <= CONTENT_LIMIT) break;
            result = removeAll(result, rule.pattern, rule.section, removed);
        }

        boolean hardTruncated = false;
        if (result.length() > CONTENT_LIMIT) {
            hardTruncated = true;
            result = result.substring(0, CONTENT_LIMIT);
        }

        result = result + TRUNCATED_NOTICE;

        return new TrimResult(result, true, hardTruncated, Set.copyOf(removed), originalLength, result.length());
    }

    private static String removeAll(String text, Pattern pattern, Section section, EnumSet<Section> removed) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return text;
        }

        removed.add(section);
        return matcher.replaceAll("");
    }

    public enum Section { INFO, MINOR, MAJOR }
    private record Rule(Section section, Pattern pattern) {}
    public record TrimResult(
            String body,
            boolean changed,
            boolean hardTruncated,
            Set<Section> removedSections,
            int originalLength,
            int finalLength) {}
}
