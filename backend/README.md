# LibreHousehold Backend

Java 25 modular monolith built with Spring Boot 4 and Spring Modulith.

## Module Structure

Each module owns its REST controllers (via the OpenAPI Delegate pattern) and its domain logic.
Modules communicate through explicit Java interfaces — not shared database tables or domain events.

```
eu.wiegandt.librehousehold/
  household/       # Household CRUD, member management, invitations, setup
  tasks/           # Task CRUD and recurrence logic
  expenses/        # Expenses, categories, financials, reimbursements
  usersettings/    # Password, preferences, account management
  notifications/   # E-mail dispatch (event-driven, see below)
  statistics/      # Aggregated statistics across tasks and expenses
  administration/  # Admin features
```

## Key Architecture Decisions

### Module coupling: interfaces, not events

Modules are decoupled through explicit Java service interfaces exposed by the owning module.
Domain events are **not** used as a general inter-module communication mechanism.

**Example — Member lookup:**
`Member` data lives exclusively in the `household` module.
Other modules store only a `memberId` (UUID) and validate or query via an exposed interface:

```java
// Exposed by the household module
public interface MemberLookup {
    boolean existsInHousehold(UUID householdId, UUID memberId);
    String getMemberName(UUID memberId);
}
```

**Rationale:**
- The modular monolith provides strong consistency for free — introducing eventual consistency has a real cost (harder debugging, harder testing, more failure modes) with no benefit at this scale.
- Explicit interfaces are immediately understandable for open-source contributors (supports QG4).
- The microservice migration path stays open: what matters is clear module boundaries and defined interfaces, not duplicated data.

### Domain events: notifications only

Application events (Spring Modulith `@ApplicationModuleListener`) are used **only** where they are
fachlich motivated — currently: the `notifications` module reacts to events such as `ExpenseCreated`
to send e-mail notifications to affected members.

### REST endpoint ownership: per module

Each module provides its own `*ApiDelegateImpl` (generated controller delegates from the OpenAPI spec).
A central `web` or `api` module that owns all controllers would contradict the principle of
fachliche Modularisierung (ADR-003) and conflict with the OpenAPI Delegate pattern already in use.

Note: REST path structure (e.g. `/household/{id}/categories`) does not dictate module ownership.
Categories belong to the `expenses` module because they are a domain concept of expense management.

### Module boundary verification

Spring Modulith verifies module boundaries at test time. No module may access the internals of
another module — only interfaces annotated with `@NamedInterface` or placed in the module root package
are part of the public API.

## Build & Run

See [AGENTS.md](../AGENTS.md) for build commands, test commands, and development setup.