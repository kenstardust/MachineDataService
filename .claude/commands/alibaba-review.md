# Alibaba-inspired Repository Review

Use this repo-local skill to review code or pending changes against Alibaba-inspired development practices adapted for this repository.

## Purpose

This skill is review-oriented, not code-generation-oriented. Prefer reviewing pending changes first. If there is no diff, review the user-requested scope; otherwise expand to the wider repository only as needed.

Treat the guidance here as Alibaba-inspired best practices, not a verbatim or authoritative restatement of any official document.

## Review priorities

Review in this order:
1. Correctness and production safety
2. Security and data protection
3. Error handling and observability
4. Maintainability and readability
5. Test completeness and regression risk
6. Style consistency

Do not flood the output with style-only suggestions when there are more important issues.

## Repository context

This repository is primarily a Spring Boot backend with Kafka, Redis, and IoTDB.

Pay extra attention to:
- Kafka consumer correctness, acknowledgment boundaries, retry impact, and duplicate processing risk
- Redis-backed idempotency and behavior when Redis is disabled
- IoTDB request validation, query/write boundaries, and failure handling
- Conditional bean behavior controlled by configuration
- Integration tests that should reflect real end-to-end behavior

## File-type review rules

### Java and test code

Check for:
- Clear package, class, method, variable, and constant naming
- Reasonable class and method responsibility boundaries
- Validation at system boundaries for request/external input
- Null-fragile logic and unclear optionality
- Overly generic exceptions, swallowed exceptions, or inconsistent exception translation
- Logs that miss key context or expose secrets/sensitive payloads
- Magic strings and magic numbers that should be constants or configuration
- Thread-safety, idempotency, and retry-safety issues
- Risky collection/stream usage that hurts clarity or correctness
- Weak test coverage around edge cases, failure cases, and integration paths

For this repo, emphasize Kafka ack/idempotency interaction, Redis toggle behavior, and IoTDB integration correctness.

### YAML, YML, properties, and config files

Check for:
- Hard-coded credentials or sensitive values
- Unsafe defaults
- Confusing or inconsistent grouping of related settings
- Timeout, TTL, retry, and feature-flag values that are unclear or risky
- Config that is likely environment-specific but committed as a fixed value
- Non-obvious operational values that need brief explanation in docs

### Markdown and documentation

Check for:
- Commands and examples matching the actual repository
- Configuration keys, endpoints, and class names matching current code
- Claims that overstate guarantees or omit prerequisites
- Ambiguous instructions that could cause incorrect setup or testing

### Build and dependency files

Check for:
- Redundant or unnecessary dependencies
- Dependency/version management that is hard to maintain
- Plugin/build settings that conflict with the repository's actual workflow

### Other file types

Apply conservative review rules only:
- Readability
- Maintainability
- Security basics
- Avoid over-applying Java-specific rules to unrelated file types

## Output format

Always structure the review as:

### Summary
Short overall assessment.

### Must fix
Only correctness, security, data-loss, concurrency, or production-risk issues.

### Should improve
Maintainability, clarity, observability, and medium-risk design issues.

### Nice to have
Low-risk consistency or style improvements.

### Open questions
Anything that depends on missing context.

### Final assessment
A brief risk summary.

## Output rules

- Cite each finding with a file path and line number when possible.
- Distinguish between:
  - Alibaba-inspired best practice
  - Repository-specific concern
- Be concrete and suggest the smallest practical fix.
- Do not claim that a rule is an official Alibaba requirement unless that wording is explicitly available in this repository.
- If no meaningful issues are found, say so clearly instead of inventing nits.
