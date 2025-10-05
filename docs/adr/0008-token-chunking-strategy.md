# ADR-0008: Token Chunking Strategy for Large Files

**Date:** 2025-10-05
**Status:** Proposed

## Context

The current implementation rejects PR reviews when token count exceeds AI provider limits:

- **Gemini limit:** 32k tokens per request
- **Ollama limit:** Varies by model (typically 4k-8k)
- **Current behavior:** Reject entire review if any file exceeds limit
- **User experience:** No feedback on large PRs, silent failure

This creates problems:
- Large files cannot be reviewed at all
- PRs with one large file fail completely
- No partial review capability
- Lost opportunity for AI feedback on reviewable portions

Large file scenarios:
- Generated code (migrations, protobuf)
- Minified JavaScript/CSS
- Long data files
- Refactoring that touches many lines

**Related Issue:** Section 6.5 of comprehensive code review report
**Component:** Token counting and AI client adapters

## Decision

Implement smart token chunking that splits large files into reviewable chunks while preserving code context.

**Strategy:**
- Detect token overflow before sending to AI
- Split files into chunks at logical boundaries
- Preserve context across chunks (function/class boundaries)
- Review each chunk separately
- Synthesize chunk reviews into coherent feedback

**Chunking approach:**
- Prefer splitting at function/method boundaries
- Preserve class context in each chunk
- Include overlap between chunks for continuity
- Add chunk metadata to prompts

## Consequences

### Positive

- Large files can be reviewed instead of rejected
- Better user experience (partial feedback vs none)
- Graceful degradation for edge cases
- Maintains review quality by preserving context
- Supports any file size (within reason)

### Negative

- Increased complexity in token management
- Multiple AI calls per large file (higher cost)
- Chunk synthesis requires careful prompt engineering
- Edge cases: chunks split mid-logic
- Potential for inconsistent feedback across chunks

## Alternatives Considered

### Alternative 1: Reject large files with explanation

**Description:** Continue rejecting but add clear error message

**Pros:**
- Simple implementation
- No additional complexity
- Clear user feedback

**Cons:**
- No review for large files
- Poor user experience
- Misses review opportunities

**Why rejected:** Provides no value, only better error message.

### Alternative 2: Summarize large files

**Description:** Use AI to summarize before reviewing

**Pros:**
- Keeps token count low
- Single AI call per file

**Cons:**
- Loses detail in summarization
- Quality depends on summary quality
- Extra AI call for summarization
- May miss important issues

**Why rejected:** Summarization loses critical details needed for code review.

### Alternative 3: Skip large files silently

**Description:** Review smaller files, skip large ones

**Pros:**
- Simple implementation
- Partial review possible

**Cons:**
- No user feedback about skipped files
- Unpredictable behavior
- Users unaware of limitations

**Why rejected:** Silent failures create confusion and mistrust.

### Alternative 4: Naive line-based chunking

**Description:** Split files every N lines

**Pros:**
- Simple implementation
- Predictable chunks

**Cons:**
- Breaks mid-function/mid-class
- No context preservation
- Poor review quality

**Why rejected:** Context-unaware chunking produces low-quality reviews.

## Implementation

### Chunking algorithm

```
1. Count tokens for file diff
2. If under limit: review normally
3. If over limit: analyze file structure
4. Find logical split points:
   - Function boundaries
   - Class boundaries
   - Module boundaries
5. Create chunks with overlap
6. Add context metadata to each chunk
7. Review chunks independently
8. Synthesize results
```

### Context preservation

Each chunk includes:
- File path and purpose
- Chunk number and total chunks
- Preceding context (previous function signature)
- Overlapping lines from previous chunk
- Following context (next function signature)

### Token accounting

Track tokens at multiple levels:
- Per file diff
- Per chunk
- Total request (all files + prompt)
- Safety margin (5% buffer)

### Chunk synthesis

Combine chunk reviews:
- Aggregate severity levels
- Deduplicate similar comments
- Maintain comment ordering by line number
- Note if review is partial due to chunking

### Validation approach

Verify chunking correctness:
1. Test with files just under limit (no chunking)
2. Test with files just over limit (minimal chunking)
3. Test with very large files (many chunks)
4. Verify context preservation at boundaries
5. Check synthesis produces coherent output
6. Validate token counting accuracy

## Edge Cases

**Binary files:**
- Detect early, skip chunking
- Report as non-reviewable

**Multiple large files in one PR:**
- Chunk each file independently
- Risk of exceeding total request budget
- May need PR-level chunking

**Generated code:**
- Detect patterns (comments, formatting)
- Consider skipping with explanation
- Or chunk but note it's generated

**Minified code:**
- High token count, low semantic value
- Skip with explanation
- Suggest unminified version

## Cost Analysis

**Without chunking:**
- Large file: Rejected, 0 AI calls
- Cost: $0
- Value: None

**With chunking:**
- Large file (10k lines): 3 chunks
- Cost: ~3x normal file cost
- Value: Actionable review feedback

Trade-off: Higher cost per large file, but provides value. Recommended to add rate limiting to prevent abuse.

## Configuration

```yaml
# Configuration approach (not actual values)
ai:
  token-limits:
    gemini: 30000  # 32k with safety margin
    ollama: 3500   # 4k with safety margin
  chunking:
    enabled: true
    overlap-lines: 20
    max-chunks-per-file: 10
    strategy: smart  # smart|naive|disabled
```

## Monitoring

Track metrics:
- Files requiring chunking
- Average chunks per file
- Token count distribution
- Chunk synthesis quality
- User satisfaction with chunked reviews

## References

- [Token Counting in LLMs](https://platform.openai.com/docs/guides/text-generation/managing-tokens)
- Code Review Report: [Section 6.5 Token Chunking](../code-review/comprehensive-code-review-report.md#65-token-chunking)
- Related: [ADR-0005: Rate Limiting Strategy](./0005-rate-limiting.md)
- Related: [ADR-0002: AI Provider Selection](./0002-automate-pr-reviews-via-github-actions.md)
