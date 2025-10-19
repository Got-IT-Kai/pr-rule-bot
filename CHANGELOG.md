# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Multi-module project structure with 5 microservices and common module ([#71])

### Changed
- Migrated build configuration to Gradle Kotlin DSL for type-safety and better IDE support ([#55])
- Migrated to Spring WebFlux for non-blocking webhook handling ([#61])

### Removed
- Removed OpenTelemetry dependencies and configuration ([#43])
- Removed unused vector database dependencies ([#44])
- Removed wildcard imports from test files ([#50])

### Fixed
- Prevented GitHub token from appearing in logs ([#37])

### Security
- Implemented webhook signature verification with HMAC-SHA256 ([#36])

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

<!-- Issue References -->
[#36]: https://github.com/Got-IT-Kai/pr-rule-bot/issues/36
[#37]: https://github.com/Got-IT-Kai/pr-rule-bot/issues/37
[#43]: https://github.com/Got-IT-Kai/pr-rule-bot/issues/43
[#44]: https://github.com/Got-IT-Kai/pr-rule-bot/issues/44
[#50]: https://github.com/Got-IT-Kai/pr-rule-bot/issues/50
[#55]: https://github.com/Got-IT-Kai/pr-rule-bot/issues/55
[#61]: https://github.com/Got-IT-Kai/pr-rule-bot/issues/61
[#71]: https://github.com/Got-IT-Kai/pr-rule-bot/issues/71

<!-- Version Comparison Links -->
[Unreleased]: https://github.com/Got-IT-Kai/pr-rule-bot/compare/master...HEAD
