# ADR-06: Spring Boot 3.3 + Java 21 toolchain

**Status:** Accepted
**Date:** 2026-05-20 (Slice 0)
**Tags:** backend, build

## Context
Need to pin a Spring Boot major and a Java toolchain version. Spring Boot 3.4 was fresh; 3.3.x was current stable. Java 21 is the current LTS; Java 17 is the previous LTS.

## Decision
Spring Boot 3.3.5 with the Java 21 toolchain (Temurin). Wrapper pinned to Gradle 8.10.2 (the version tested by Spring Boot 3.3.5).

## Rationale
- 3.3.x is stable, well-documented, and matches the version every recent tutorial assumes; no reason to chase 3.4 the week it shipped.
- Java 21 LTS unlocks virtual threads, pattern matching, and records — all useful in service code we're about to write.
- Toolchain + wrapper together pin every contributor and CI agent to identical versions, regardless of what's installed system-wide.

## Consequences
- Spring Boot upgrade cadence is "stable line, opportunistically" — we'll move to 3.4 when there's a concrete reason.
- Library choices (JJWT 0.12, AWS SDK v2, Spring Data ES) must be compatible with Spring Boot 3.3's BOM.
- Virtual threads available where appropriate (e.g., blocking I/O fan-out); not adopted blanket-wide.
