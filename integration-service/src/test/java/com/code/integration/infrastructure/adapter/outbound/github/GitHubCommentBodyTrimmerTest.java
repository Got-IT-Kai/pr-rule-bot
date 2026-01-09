package com.code.integration.infrastructure.adapter.outbound.github;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@DisplayName("GitHubCommentBodyTrimmer")
class GitHubCommentBodyTrimmerTest {

    private static final String INFO_START = "<!--SECTION:INFO:START-->";
    private static final String INFO_END   = "<!--SECTION:INFO:END-->";
    private static final String MINOR_START = "<!--SECTION:MINOR:START-->";
    private static final String MINOR_END   = "<!--SECTION:MINOR:END-->";
    private static final String MAJOR_START = "<!--SECTION:MAJOR:START-->";
    private static final String MAJOR_END   = "<!--SECTION:MAJOR:END-->";

    private static final int MAX_COMMENT_LENGTH = 60_000;
    private static final String TRUNCATED_NOTICE =
            "\n\n---\nThis review was truncated. See logs for full content.";
    private static final int CONTENT_LIMIT = MAX_COMMENT_LENGTH - TRUNCATED_NOTICE.length();


    private GitHubCommentBodyTrimmer trimmer;

    private static String section(String name, String content) {
        return "<!--SECTION:" + name + ":START-->\n"
                + content
                + "\n<!--SECTION:" + name + ":END-->";
    }

    @BeforeEach
    void setUp() {
        trimmer = new GitHubCommentBodyTrimmer();
    }

    @Test
    @DisplayName("Null input -> returns empty result with no changes")
    void nullInput_returnsEmptyResult() {
        GitHubCommentBodyTrimmer.TrimResult trimResult = trimmer.trimWithMetadata(null);
        assertThat(trimResult.body()).isEmpty();
        assertThat(trimResult.changed()).isFalse();
        assertThat(trimResult.removedSections()).isEmpty();
        assertThat(trimResult.originalLength()).isZero();
        assertThat(trimResult.finalLength()).isZero();
    }

    @Test
    @DisplayName("Empty input -> returns empty result with no changes")
    void emptyInput_returnsEmpty() {
        GitHubCommentBodyTrimmer.TrimResult trimResult = trimmer.trimWithMetadata("");

        assertThat(trimResult.body()).isEmpty();
        assertThat(trimResult.changed()).isFalse();
        assertThat(trimResult.hardTruncated()).isFalse();
        assertThat(trimResult.removedSections()).isEmpty();
        assertThat(trimResult.originalLength()).isZero();
        assertThat(trimResult.finalLength()).isZero();
    }

    @Test
    @DisplayName("Under limit -> does not modify (even if markers exist)")
    void underLimit_doesNotModify() {

        String body = "prefix\n" + section("INFO", "small-info") + "\nsuffix\n";
        assertThat(body.length()).isLessThan(MAX_COMMENT_LENGTH);

        GitHubCommentBodyTrimmer.TrimResult trimResult = trimmer.trimWithMetadata(body);

        assertThat(trimResult.changed()).isFalse();
        assertThat(trimResult.hardTruncated()).isFalse();
        assertThat(trimResult.removedSections()).isEmpty();
        assertThat(trimResult.body()).isEqualTo(body);
        assertThat(trimResult.originalLength()).isEqualTo(body.length());
        assertThat(trimResult.finalLength()).isEqualTo(body.length());
    }

    @Test
    @DisplayName("Exactly at limit -> does not modify")
    void atLimit_doesNotModify() {
        String body = "B".repeat(MAX_COMMENT_LENGTH);
        GitHubCommentBodyTrimmer.TrimResult trimResult = trimmer.trimWithMetadata(body);

        assertThat(trimResult.changed()).isFalse();
        assertThat(trimResult.body()).isEqualTo(body);
        assertThat(trimResult.finalLength()).isEqualTo(MAX_COMMENT_LENGTH);
    }

    @Test
    @DisplayName("Over limit + no markers -> hard truncates and appends notice")
    void overLimit_noMarkers_hardTruncate_andNotice() {
        String body = "B".repeat(MAX_COMMENT_LENGTH + 10_000);

        GitHubCommentBodyTrimmer.TrimResult trimResult = trimmer.trimWithMetadata(body);

        assertThat(trimResult.changed()).isTrue();
        assertThat(trimResult.hardTruncated()).isTrue();
        assertThat(trimResult.removedSections()).isEmpty();

        assertThat(trimResult.body()).endsWith(TRUNCATED_NOTICE);
        assertThat(trimResult.finalLength()).isLessThanOrEqualTo(MAX_COMMENT_LENGTH);
        assertThat(trimResult.body().length()).isEqualTo(CONTENT_LIMIT + TRUNCATED_NOTICE.length());
    }

    @Test
    @DisplayName("Over limit: INFO removal alone is enough -> removes INFO only; no hard truncation")
    void overLimit_infoRemovalIsEnough_removesInfoOnly() {
        String core = "C".repeat(40_000);
        String info = section("INFO", "I".repeat(30_000));

        String body = core + "\n" + info;

        assertThat(body.length()).isGreaterThan(MAX_COMMENT_LENGTH);

        GitHubCommentBodyTrimmer.TrimResult trimResult = trimmer.trimWithMetadata(body);

        assertThat(trimResult.changed()).isTrue();
        assertThat(trimResult.hardTruncated()).isFalse();
        assertThat(trimResult.removedSections())
                .containsExactlyInAnyOrder(GitHubCommentBodyTrimmer.Section.INFO);

        assertThat(trimResult.body()).doesNotContain(INFO_START).doesNotContain(INFO_END);
        assertThat(trimResult.body()).endsWith(TRUNCATED_NOTICE);
        assertThat(trimResult.finalLength()).isLessThanOrEqualTo(MAX_COMMENT_LENGTH);
    }

    @Test
    @DisplayName("Over limit: remove MINOR before hard truncating when MINOR can make enough room")
    void overLimit_minorRemoval_preferredOverHardTruncate_whenEnough() {
        String minor = section("MINOR", "m".repeat(500));

        // make (core + minor) be CONTENT_LIMIT + 1
        int coreLen = (CONTENT_LIMIT + 1) - minor.length();
        assertThat(coreLen).isGreaterThan(0);

        String core = "C".repeat(coreLen);

        // add INFO to push original over MAX (INFO should be removed first)
        String info = section("INFO", "I".repeat(5_000));

        String body = core + minor + info;

        assertThat(body.length()).isGreaterThan(MAX_COMMENT_LENGTH);

        GitHubCommentBodyTrimmer.TrimResult trimResult = trimmer.trimWithMetadata(body);

        assertThat(trimResult.changed()).isTrue();
        assertThat(trimResult.hardTruncated()).isFalse();
        assertThat(trimResult.removedSections())
                .contains(GitHubCommentBodyTrimmer.Section.INFO, GitHubCommentBodyTrimmer.Section.MINOR)
                .doesNotContain(GitHubCommentBodyTrimmer.Section.MAJOR);

        assertThat(trimResult.body())
                .doesNotContain(INFO_START).doesNotContain(INFO_END)
                .doesNotContain(MINOR_START).doesNotContain(MINOR_END);
        assertThat(trimResult.body()).endsWith(TRUNCATED_NOTICE);
        assertThat(trimResult.finalLength()).isLessThanOrEqualTo(MAX_COMMENT_LENGTH);
    }

    @Test
    @DisplayName("Over limit: removes INFO->MINOR->MAJOR before hard truncating (if MAJOR removal makes it fit)")
    void overLimit_removeInfoMinorMajor_beforeHardTruncate_whenPossible() {
        String info  = section("INFO",  "I".repeat(2_000));
        String minor = section("MINOR", "m".repeat(2_000));
        String major = section("MAJOR", "M".repeat(2_000));

        // after removing INFO and MINOR, we want (core + major) be CONTENT_LIMIT + 1 -> MAJOR must be removed
        int coreLen = (CONTENT_LIMIT + 1) - major.length();
        assertThat(coreLen).isGreaterThan(0);

        String core = "C".repeat(coreLen);

        String body = core + major + minor + info;

        assertThat(body.length()).isGreaterThan(MAX_COMMENT_LENGTH);

        GitHubCommentBodyTrimmer.TrimResult trimResult = trimmer.trimWithMetadata(body);

        assertThat(trimResult.changed()).isTrue();
        assertThat(trimResult.hardTruncated()).isFalse();
        assertThat(trimResult.removedSections())
                .containsExactlyInAnyOrder(
                        GitHubCommentBodyTrimmer.Section.INFO,
                        GitHubCommentBodyTrimmer.Section.MINOR,
                        GitHubCommentBodyTrimmer.Section.MAJOR
                );

        assertThat(trimResult.body())
                .doesNotContain(INFO_START).doesNotContain(INFO_END)
                .doesNotContain(MINOR_START).doesNotContain(MINOR_END)
                .doesNotContain(MAJOR_START).doesNotContain(MAJOR_END)
                .endsWith(TRUNCATED_NOTICE);
        assertThat(trimResult.finalLength()).isLessThanOrEqualTo(MAX_COMMENT_LENGTH);
    }

    @Test
    @DisplayName("Over limit: even after removing all sections, still too long -> hard truncates and appends notice")
    void overLimit_stillTooLongAfterAllRemovals_hardTruncates() {
        // core alone is already too large for CONTENT_LIMIT, so even removing all blocks won't help
        String core = "C".repeat(CONTENT_LIMIT + 10_000);
        String info  = section("INFO", "I".repeat(100));
        String minor = section("MINOR", "m".repeat(100));
        String major = section("MAJOR", "M".repeat(100));

        String body = core + info + minor + major;

        assertThat(body.length()).isGreaterThan(MAX_COMMENT_LENGTH);

        GitHubCommentBodyTrimmer.TrimResult trimResult = trimmer.trimWithMetadata(body);

        assertThat(trimResult.changed()).isTrue();
        assertThat(trimResult.hardTruncated()).isTrue();
        assertThat(trimResult.removedSections())
                .contains(GitHubCommentBodyTrimmer.Section.INFO,
                        GitHubCommentBodyTrimmer.Section.MINOR,
                        GitHubCommentBodyTrimmer.Section.MAJOR);

        assertThat(trimResult.body()).endsWith(TRUNCATED_NOTICE);
        assertThat(trimResult.finalLength()).isLessThanOrEqualTo(MAX_COMMENT_LENGTH);
        assertThat(trimResult.body().length()).isEqualTo(CONTENT_LIMIT + TRUNCATED_NOTICE.length());
    }

    @Test
    @DisplayName("Multiple INFO blocks -> removes all occurrences and records INFO once")
    void multipleInfoBlocks_removedAllOccurrences() {
        String core = "C".repeat(CONTENT_LIMIT + 5_000);
        String info1 = section("INFO", "I".repeat(1_000));
        String info2 = section("INFO", "i".repeat(1_000));

        String body = core + info1 + info2;

        assertThat(body.length()).isGreaterThan(MAX_COMMENT_LENGTH);

        GitHubCommentBodyTrimmer.TrimResult trimResult = trimmer.trimWithMetadata(body);

        assertThat(trimResult.changed()).isTrue();
        assertThat(trimResult.removedSections()).contains(GitHubCommentBodyTrimmer.Section.INFO);

        // both blocks should be removed
        assertThat(trimResult.body()).doesNotContain(INFO_START).doesNotContain(INFO_END);
        assertThat(trimResult.body()).endsWith(TRUNCATED_NOTICE);
    }

    @Test
    @DisplayName("removedSections is unmodifiable (Set.copyOf)")
    void removedSections_isUnmodifiable() {
        String core = "C".repeat(40_000);
        String info = section("INFO", "I".repeat(30_000));

        String body = core + "\n" + info;

        GitHubCommentBodyTrimmer.TrimResult trimResult = trimmer.trimWithMetadata(body);

        assertThat(trimResult.removedSections()).contains(GitHubCommentBodyTrimmer.Section.INFO);
        assertThatThrownBy(() -> trimResult.removedSections().add(GitHubCommentBodyTrimmer.Section.MINOR))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
