# Archive

This directory contains files that are no longer actively used but kept for reference.

## Ollama Integration (Archived)

The project initially used Ollama for local LLM inference but switched to Google Gemini due to performance issues.

### Performance Issues
- Ollama with `qwen2.5-coder:3b` model took >5 minutes for simple code reviews
- GitHub Actions timeout constraints made it impractical for CI/CD
- Gemini 2.0 Flash provides much faster responses (~10-30 seconds)

### Archived Files
- `Dockerfile.ollama` - Docker configuration for Ollama with pre-loaded models
- `docker-compose.ci.yml` - Docker Compose setup for Ollama in CI
- `test-3b-image.sh` - Test script for validating Ollama Docker images

### Deleted Docker Images
- `ghcr.io/got-it-kai/pr-rule-bot-ollama:7b` (16.6GB)
- `ghcr.io/got-it-kai/pr-rule-bot-ollama:3b` (8.73GB)
- `ollama/ollama:latest` (7.29GB)

### Total Space Freed
~32.6GB of Docker images removed

## Future Considerations

If you want to re-enable Ollama:
1. Restore files from this archive
2. Update `.github/workflows/ai-code-review.yml` to uncomment Ollama setup
3. Consider using more powerful GitHub-hosted runners (8-core, 32GB RAM)
4. Or use self-hosted runners with GPU support for better performance

## Current AI Provider

**Google Gemini 2.0 Flash** via Workload Identity Federation
- Fast response times
- Free tier available
- No local model hosting required
