# ServiceDesk Project Documentation

**Project**: ServiceDesk, Intelligent IT Service Management & Incident Resolution System  
**Course**: CS F213 / MAC F212 Object-Oriented Programming, Semester I, 2026-2027 (Group 58)

This directory contains technical and design specifications for **BITS ServiceDesk**, structured around clean object-oriented architecture and Gang of Four (GoF) design patterns.

---

## 1. System Documentation Index

| Document | Description |
| :--- | :--- |
| **[Problem Analysis](problem-analysis.md)** | Problem definition, stakeholder analysis, technical challenges, proposed solution, and foundational assumptions. |
| **[Requirements & Business Rules](requirements.md)** | Functional (FR) & Non-Functional (NFR) requirements, roles matrix, priority formula, SLA matrix, allowed transitions, and escalation rules. |
| **[Architecture & Package Layout](architecture.md)** | Hexagonal / Clean Architecture overview, 5 named owner areas, and full class/package layout. |
| **[Design Patterns Catalog](design-patterns.md)** | The eight patterns in use (State, Strategy, Observer, Factory, Repository, Adapter, Chain of Responsibility, Decorator) with the classes involved. |
| **[REST API Specification](api-spec.md)** | REST endpoints per role, HTTP verbs, request/response DTOs, and RBAC rules. |
| **[Database Schema Design](database.md)** | Relational schema, PostgreSQL tables, indexing strategies, and Flyway migration plan. |
| **[Project Plan & MoSCoW Scope](project-plan.md)** | Day-by-day plan (8 Nov – 22 Nov), milestone targets, and explicit MUST / SHOULD / COULD scope. |
| **[Risks & Assumptions](risks-and-assumptions.md)** | Technical risk mitigation strategies and key assumptions for confirmation. |
| **[Test Cases Specification](test-cases.md)** | 110 documented automated test cases and 16 manual end-to-end checks. |
| **[Security](security.md)** | Authentication, authorisation, rate limiting and secret handling. |
| **[Technology Stack Report](tech-stack-report.md)** | Why each technology was chosen, alternatives and limits. |
| **[Course Demo Script](demo-script.md)** | Step-by-step walkthrough of the three demonstration workflows. |

---

## 2. Mermaid Visual Models (`docs/diagrams/`)

- **[Class Diagram](diagrams/class-diagram.mmd)**: Domain classes, attributes, methods, interfaces, inheritance, composition, and GoF design pattern locations.
- **[State Diagram](diagrams/state-diagram.mmd)**: Incident lifecycle state machine, needs-info loop, SLA pause/resume, and forbidden transition guards.
- **[Use Case Diagram](diagrams/use-case-diagram.mmd)**: Use cases for Employee, Technician, Administrator, and System Scheduler.
- **[Entity-Relationship (ER) Diagram](diagrams/er-diagram.mmd)**: Database entities, primary/foreign keys, relationships, and constraints.
- **[Sequence Diagram](diagrams/sequence-diagram.mmd)**: End-to-end execution sequence (Report incident -> Classify -> Prioritize -> Assign -> Notify -> Audit).
