# PR Rule Bot
_A cross-platform Git pull/merge request reviewer powered by Large Language Models._

## Goal
Boost developer productivity by off-loading repetitive code-review checks to an LLM that knows your team’s conventions.

## Roadmap
| Phase  | Focus                               | Status |
|--------|-------------------------------------|--------|
| **Foundation** | Service skeleton & CI               | In Progress |
| **Webhook Integration** | Git Repository listener             | Planned |
| **Inline Reviewer MVP** | Policy-based comments               | Planned |
| **Knowledge-Augmented Reviewer** | RAG + Function Calling              | Planned |
| **Multi-Policy Engine** | Security / style / perf in parallel | Planned |
| **Operational Excellence** | Metrics, Helm, alerts               | Planned |
| **GA** | Stable API & docs                   | Planned |

## Runtime & Tools
| Stack | Version | Why |
|-------|---------|-----|
| Java | 21+ | Modern language features, long-term support |
| Gradle | 8.5+ | Conventional builds, version catalogs |
| Spring Boot | 3.5.3+ | Native records, virtual threads |
| Docker Desktop | latest | One-click local stack |
| Ollama | latest | Run local LLMs without vendor lock-in |
| ChromaDB | latest | Lightweight vector store for RAG |

## License
Code: Apache-2.0 — see [LICENSE](LICENSE).  

Docs & slides © 2025 Kai L., all rights reserved.