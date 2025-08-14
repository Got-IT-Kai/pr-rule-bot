# 0002: Automate PR Reviews via GitHub Actions and Managed AI API
* **Date**: 2025-08-14
* **Status**: Accepted

## Context
The goal is to provide automated code review comments on pull requests to improve code quality and maintain consistency.

This review should only be triggered after CI checks (build, tests) have passed, ensuring the review is performed on stable, valid code.

The initial approach considered using a self-hosted Ollama server.

However, this poses significant challenges:
* Availability
    * It requires maintaining a dedicated, managed server, which is not currently available for this project.
* Synchronization
    * A self-hosted server (e.g., on Cloudflare) might not be running the latest version
* CI Integration
    * Running the review logic requires the CI environment to have network access to this external server.

## Decision
Use GitHub Actions to automate PR reviews

1. The workflow will be triggered by pull_request events.
2. Instead of self-hosting a model, I will use a managed, serverless AI API (e.g., Google's Gemini API).
3. The workflow will pass the PR's diff to the AI API and post the returned review as a comment on the PR.
4. All necessary API keys and tokens will be stored securely using GitHub Encrypted Secrets.

## Consequences
### Positive:
* No Server Maintenance
  * Eliminates the operational overhead of managing and updating a dedicated AI model server.
* Always Uses Latest Logic
    * The review logic is defined within the repository's workflow file.
    * Ensuring every review uses the most up-to-date prompts and process.
* Secure
    * API keys and tokens are not exposed in logs or source code.
    * Managed entirely by GitHub's secure infrastructure.
* Simplified Architecture
    * The entire review process is self-contained within the GitHub repository and its Actions. 
### Negative:
* External Dependency
  * The review process now depends on the availability and terms of a third-party AI service (e.g., Google, OpenAI).
* Potential Cost
    * While many services offer free tiers, high usage may incur costs in the future.