# ADR-0008 - Modern Java LTS and Spring Boot Baseline

- Status: Accepted as planning baseline; exact patch pins verified in Prompt 00
- Date: 2026-08-10

## Context

The project exists partly to demonstrate current production-style Java/Spring engineering rather than only older academic Java work.

## Decision

Plan around:

- Java 25 LTS;
- Spring Boot 4.1.x at planning time;
- modern Maven/JUnit/Testcontainers tooling.

Prompt 00 must verify current official patch releases and compatibility before initialization.

Avoid preview language features in public/domain/SDK APIs unless separately justified.

Prompt 00 verification on 2026-08-10 selected Eclipse Temurin `25.0.4+7`, Spring Boot `4.1.0`, and Maven `3.9.16` through Maven Wrapper Plugin `3.3.4`. The complete toolchain record and official release references are maintained in `docs/19_TECHNOLOGY_BASELINE.md`.

## Consequences

- strong modern Java signal;
- current ecosystem;
- requires developers/CI to install the selected LTS;
- exact dependency compatibility must be pinned and tested.
