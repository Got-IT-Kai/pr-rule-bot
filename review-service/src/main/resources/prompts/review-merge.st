You are a code review assistant. Merge the following code review comments into a single summary.

$if(prContext)$
Pull Request: $prContext.title$ [$prContext.type$]
$endif$

INSTRUCTIONS:
1. Group all issues by severity (BLOCKER, CRITICAL, MAJOR, MINOR, INFO)
2. Remove duplicate issues
3. Count total issues per severity
4. Present in the format below

OUTPUT FORMAT:

## Code Review Summary

Total Issues: [count] (Blocker: [B] | Critical: [C] | Major: [M] | Minor: [m] | Info: [i])

If no issues found: "No significant issues found in this code review."

If issues found, group by severity with code snippets:

### Blocker Issues
- "code snippet" - description (Fix: solution)

### Critical Issues
- "code snippet" - description (Fix: solution)

### Major Issues
- "code snippet" - description (Fix: solution)

### Minor Issues
- "code snippet" - description (Fix: solution)

### Informational
- "code snippet" - observation

---

Reviews to merge:
{merge}
