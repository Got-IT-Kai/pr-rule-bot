# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### v1.0 Release Plan

First production release focusing on core AI-powered code review functionality with security and stability improvements.

#### Security Hardening (Critical Priority)
- [#36](https://github.com/Got-IT-Kai/pr-rule-bot/issues/36) Implement webhook signature verification
- [#37](https://github.com/Got-IT-Kai/pr-rule-bot/issues/37) Prevent GitHub token from appearing in logs
- [#38](https://github.com/Got-IT-Kai/pr-rule-bot/issues/38) Add AI prompt injection protection

#### Core Bug Fixes (High Priority)
- [#39](https://github.com/Got-IT-Kai/pr-rule-bot/issues/39) Fix bot identity management preventing bot reviews
- [#40](https://github.com/Got-IT-Kai/pr-rule-bot/issues/40) Fix memory leak in large PR processing
- [#41](https://github.com/Got-IT-Kai/pr-rule-bot/issues/41) Fix event publisher blocking in reactive pipeline

#### Smart Token Chunking (High Priority)
- [#42](https://github.com/Got-IT-Kai/pr-rule-bot/issues/42) Implement smart token chunking for large files

#### Infrastructure Cleanup (Medium Priority)
- [#43](https://github.com/Got-IT-Kai/pr-rule-bot/issues/43) Remove OTEL dependencies and configuration
- [#44](https://github.com/Got-IT-Kai/pr-rule-bot/issues/44) Remove unused vector database dependencies

#### Test Coverage (High Priority)
- [#45](https://github.com/Got-IT-Kai/pr-rule-bot/issues/45) Improve AI adapter test coverage (43% → 80%)
- [#46](https://github.com/Got-IT-Kai/pr-rule-bot/issues/46) Add webhook controller integration tests (10% → 80%)
- [#47](https://github.com/Got-IT-Kai/pr-rule-bot/issues/47) Add GitHub event model tests (13% → 80%)

#### Code Quality (Low Priority)
- [#48](https://github.com/Got-IT-Kai/pr-rule-bot/issues/48) Add JavaDoc documentation for public APIs
- [#49](https://github.com/Got-IT-Kai/pr-rule-bot/issues/49) Extract magic numbers to named constants
- [#50](https://github.com/Got-IT-Kai/pr-rule-bot/issues/50) Remove wildcard imports from test files

#### Deferred to v2.0
- Rate Limiting (ADR-0005) - Not needed for single-user personal project
- Circuit Breaker Pattern (ADR-0007) - Overengineering for v1.0
- OTEL Observability (ADR-0006) - Comprehensive observability deferred

#### Architecture Decisions
- [ADR-0003](docs/adr/0003-webhook-security.md) - Webhook Security
- [ADR-0004](docs/adr/0004-bot-identity-management.md) - Bot Identity Management
- [ADR-0005](docs/adr/0005-rate-limiting.md) - Rate Limiting Strategy (Deferred)
- [ADR-0006](docs/adr/0006-observability-strategy.md) - Observability Strategy (Deferred)
- [ADR-0007](docs/adr/0007-circuit-breaker-pattern.md) - Circuit Breaker Pattern (Deferred)
- [ADR-0008](docs/adr/0008-token-chunking-strategy.md) - Token Chunking Strategy

---

## Template for Future Releases

## [X.Y.Z] - YYYY-MM-DD

### Added
- New features

### Changed
- Changes to existing functionality

### Deprecated
- Features that will be removed in upcoming releases

### Removed
- Features removed in this release

### Fixed
- Bug fixes

### Security
- Security improvements and fixes

[Unreleased]: https://github.com/Got-IT-Kai/pr-rule-bot/compare/master...HEAD
