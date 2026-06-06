# LibreHousehold Context for AI Assistants

This document is the canonical AI-assistant reference for LibreHousehold and should remain the single maintained source for project-specific assistant guidance.

## Project Overview

See [README.adoc](README.adoc) for the project description, goals, and feature list.
See [Arc42 Chapter 1](docs/architecture/chapters/01_introduction_and_goals.adoc) for the quality goals (QG1–QG4).

## Architecture Big Picture

LibreHousehold is a **Modular Monolith** designed for household management. It enforces strict separation between Frontend and Backend via an API-first approach.

- **Frontend:** SvelteKit 5 application (SPA/PWA)
- **API:** Defined in `api/openapi.yml`
- **Backend:** Java-based modular monolith (currently still WIP / mostly empty)
- **Documentation:** Arc42/AsciiDoc architecture documentation handled by `docToolchain`
- **External System:** E-Mail server for notifications

Architecture decisions are documented in [`docs/architecture/adrs/`](docs/architecture/adrs/).
System actors and module responsibilities are documented in [Arc42 Chapter 3](docs/architecture/chapters/03_context_and_scope.adoc) and [Arc42 Chapter 5](docs/architecture/chapters/05_building_block_view.adoc).

### Data & State Flow

1. **API Schema:** `api/openapi.yml` is the single source of truth for the data model.
2. **Code Generation:** Frontend client types and fetch logic are generated from the OpenAPI spec via `npm run openapi`.
3. **State Management:**
   - Uses **Svelte 5 Runes** (`.svelte.ts` files like `householdState.svelte.ts`) for reactive global state.
   - Traditional stores are also present; verify whether they should be migrated to Runes.
4. **Mocking:** `api/mokapi.js` provides a fake backend for frontend development.

## Critical Workflows

### Development

**Frontend:**
Run all frontend commands from the `frontend/` directory:
```bash
cd frontend
npm install
npm run dev                                        # uses mokapi (default)
VITE_API_URL=http://localhost:8080/v1 npm run dev  # uses local Spring backend
```

The `VITE_API_URL` env var controls the API proxy target. It is parsed at dev-server startup to set the proxy host and path prefix.

**Backend:**
Run all backend commands from the `backend/` directory using the maven wrapper.

For local development with PostgreSQL and Grafana LGTM (observability) in containers:
```bash
cd backend
./mvnw spring-boot:test-run -Dspring-boot.run.main-class=eu.wiegandt.librehousehold.TestLibrehouseholdApplication
```
This starts `TestLibrehouseholdApplication`, which uses Testcontainers to automatically spin up PostgreSQL and Grafana LGTM. No manual DB setup required.

For a plain start without containers (requires an external DB):
```bash
cd backend
./mvnw spring-boot:run
```

### API Updates

When `api/openapi.yml` changes, regenerate the API clients:

**Frontend:**
```bash
cd frontend
npm run openapi
```
This cleans `frontend/src/generated-sources/openapi` and regenerates it.

**Backend:**
```bash
cd backend
./mvnw clean compile
```
This regenerates the API interfaces and models into `backend/target/generated-sources/openapi`. **Do not edit these generated Java files manually.**

### Testing & Quality

**Frontend (`frontend/`):**
- **Unit Tests:** `npm run test:unit` (Vitest). Run tests with `npm run test`
- **Linting:** `npm run lint` (ESLint + Prettier)
- **Formatting:** `npm run format`
- **Type Check:** `npm run check`
- **Translating:** Run `npm run paraglide` after updating/adding keys in `messages/de.json` or `messages/en.json`

**Backend (`backend/`):**
- **Tests:** `./mvnw test` (JUnit 5, Testcontainers)
- **Format:** `./mvnw sortpom:sort` (for `pom.xml`)

## Development Context

### Project Structure

```text
LibreHousehold/
├── backend/           # Java backend (modular monolith, Spring Boot)
├── frontend/          # TypeScript PWA
├── docs/
│   └── architecture/  # Arc42 architecture documentation
│       ├── adrs/      # Architecture Decision Records
│       └── chapters/  # Arc42 chapters
├── build/             # Build artifacts
└── README.adoc        # Project README
```

### Important Design Principles

1. **Modularity:** Clear module boundaries with defined interfaces
2. **Offline-First:** App should work without internet connection
3. **Responsive Design:** Mobile-first approach
4. **Self-hostable:** Docker containers for easy deployment
5. **Open Source:** Community-friendly and contribution-encouraging

## Technology Stack

### Backend

- **Language:** Java 25
- **Framework:** Spring Boot 4 + Spring Modulith
- **Database:** PostgreSQL with Flyway and Spring Data JDBC
- **Architecture:** Modular Monolith (Modulith)
- **Deployment:** Docker container
- **Security:** Argon2id for password hashing (ADR-009)

### Frontend

- **Type:** Progressive Web App (PWA)
- **Language:** TypeScript
- **Framework:** SvelteKit 5
- **State Management:** Svelte stores plus Svelte 5 Runes
- **i18n:** Paraglide (`$lib/paraglide/messages.js`)
- **Styling:** DaisyUI 5 + Tailwind CSS 4
- **OpenAPI Client:** Generated TypeScript client under `src/generated-sources/openapi`

### External Integrations

- E-Mail server for notifications and invitations

## Conventions & Patterns

### Frontend (SvelteKit)

- **Styling:** Tailwind CSS with DaisyUI components
- **Internationalization:** Uses Paraglide JS. Text should be keyed in `messages/` and imported.
- **Project Structure:**
  - `src/routes`: SvelteKit pages and layouts
  - `src/lib`: Shared components, utilities, and stores
  - `src/lib/stores`: State management
  - `src/generated-sources`: **Do not edit manually**; generated API clients

### Documentation

- Architecture documentation lives in `docs/architecture/`
- Use AsciiDoc (`.adoc`) for architecture/project documentation

### Store Pattern

Stores use the pattern with `Configuration` and an `Api` class:

```ts
const apiConfig = new Configuration({ basePath: '/api' });
const api = new TasksApi(apiConfig);

export const functionName = async (
  householdId: string,
  ...
): Promise<ReturnType> => {
  // API call
  // Store update
};
```

## Integration Points

- **API Client:** Imported from `$lib/api` or generated sources
- **Mock Server:** Configured in `api/mokapi.js`. **`api/mokapi.js` is off-limits — do not modify it unless the user explicitly asks you to.**

## State Management Rules

- **No localStorage/sessionStorage writes in frontend code.** State is intentionally kept in-memory only (Svelte stores/runes). Do not persist state to localStorage or sessionStorage.
- **Use Paraglide's `setLocale()`** for language changes — never write the language to localStorage manually.
- Theme changes are applied via `document.documentElement.setAttribute('data-theme', ...)` only.

## Important Frontend Files

| File | Description |
| --- | --- |
| `frontend/src/lib/stores/taskStore.ts` | Store for tasks with API functions |
| `frontend/src/lib/stores/memberStore.ts` | Store for household members |
| `frontend/src/lib/stores/householdState.svelte.ts` | Svelte 5 Rune-based state for the household |
| `frontend/src/lib/taskDueCalculator.ts` | Logic for due dates and recurrence |
| `frontend/src/routes/app/tasks/[[new]]/+page.svelte` | Task overview and creation page |

## Key Directories

- `frontend/`: Main web application
- `api/`: OpenAPI specifications and mock server data
- `backend/`: Java 25 backend
- `docs/`: Architecture documentation (AsciiDoc)
- `build/`: Build artifacts (for example the microsite)

## Important Notes for AI Assistants

1. **Maintainability is priority:** Code should be understandable for open-source contributions
2. **Respect module boundaries:** Modules communicate only through defined interfaces
3. **Mobile-first:** UI/UX decisions should consider mobile devices
4. **Security:** Handle sensitive data (expenses) securely
5. **Deployment:** Changes should consider Docker containerization

## Guardrails for AI Agents

### Workflow Rules

1. Create a plan before implementing larger changes.
2. Use this file (`AGENTS.md`) as the central reference for project-specific context.
3. Keep implementation aligned with architecture, module boundaries, and maintainability goals.

### Svelte Tooling

- Call `svelte-list-sections` first to discover available documentation sections.
- Use `svelte-get-documentation` to fetch all relevant sections at once.
- `svelte-svelte-autofixer` should be used before sending Svelte code back to the user.
- Only use `svelte-playground-link` after user confirmation.

### Context7

- Use Context7 for code generation, setup questions, and API/library documentation.

### OpenAPI Development

- Validate OpenAPI changes before committing.
- Ensure all new schemas and properties have `example` values.
- Use `PATCH` for partial resource updates, with the request body containing the fields to update.
- Follow REST best practices: the path identifies the resource, the body contains the changes.
- After OpenAPI changes, regenerate the TypeScript client with `npm run openapi`.

### Test-Driven Development (TDD)

- Follow TDD for business logic: write tests first, then implement, then refactor.
- Unit tests are required for pure functions and business logic.
- UI-only changes (visual/styling only) do not require tests.
- Test files should be named `*.spec.ts` next to the source file where practical.
- Run relevant tests as part of validation.

### Mapper-Tests (Java)

- MapStruct-Mapper sind interne Kollaborateure desselben Moduls und werden in Service-Tests **nie** gemockt.
- Stattdessen die echte Implementierung per `@Spy MemberMapper mapper = Mappers.getMapper(MemberMapper.class)` injizieren — funktioniert mit Mockito `@InjectMocks`.
- Mapper-Tests (`*MapperTest.java`) prüfen jede public Mapping-Methode in einer eigenen `@Nested`-Klasse. Kein Instancio in Mapper-Tests — Eingabedaten immer explizit konstruieren.

### UI Conventions

- **Dialogs/Confirmations:** Immer DaisyUI-Modals (`<dialog class="modal">`) verwenden — niemals die nativen Browser-APIs `confirm()`, `alert()` oder `prompt()`. Das `<dialog>`-Element mit `bind:this` auf einer `$state`-Variable binden und `.showModal()` / `.close()` programmatisch aufrufen.

```svelte
let modal: HTMLDialogElement | null = $state(null);
// Öffnen:
onclick={() => modal?.showModal()}
// Schließen (per Cancel-Button):
<form method="dialog"><button class="btn">Abbrechen</button></form>
// Schließen (programmatisch):
modal?.close();
```

### Error Handling

- Show toast notifications on errors using the toast store.
- Use optimistic updates for UI responsiveness where appropriate.
- Background saves should not block user interaction.

## Findings & Notes

### Spring Data JDBC Records mit client-generierten UUIDs

Entities als Records mit client-generierten UUIDs müssen `Persistable<UUID>` implementieren und `isNew() = true` zurückgeben, da Spring Data JDBC sonst anhand der nicht-null UUID ein UPDATE statt eines INSERT ausführt — das bei leerer Tabelle lautlos scheitert.

Da Records unveränderlich sind, darf `save()` nur für neue Entities verwendet werden. Updates werden über explizite `@Modifying @Query`-Methoden im Repository abgewickelt:

```java
@Modifying
@Query("UPDATE household SET name = :name WHERE id = :id")
void updateName(@Param("id") UUID id, @Param("name") String name);
```

### Open Design Questions: Account & Email Model

The following architectural questions need a decision before user management is implemented:

- **Email uniqueness scope**: Currently `member.email` is globally unique (system-wide UNIQUE constraint). It needs to be decided whether uniqueness should be system-wide or per-household.
- **Account–Household relationship**: Should a user account be tied to one specific household, or can a single account belong to multiple households?
- **Missing endpoint**: An endpoint is needed to check whether a given e-mail address is already registered before household setup — otherwise the client gets a generic 409 with no actionable information.
- **Existing accounts**: It is unresolved whether a user with an existing account should be able to set up a new household or whether accounts are inherently household-scoped.

### Task Schema

The `Task` schema has a `done` field (date format) that either contains a date or is `null`.

### Recurring Tasks

- `recurring: boolean`
- `recurrenceUnit`: days, weeks, months, years
- `recurrenceInterval`: count (minimum `1`)
- For recurring tasks, the task is considered done only if the done date is after the last due date.

### Cross-Module Communication (ADR-011)

Entschieden in [ADR-011](docs/architecture/adrs/adr-011.adoc). Kurzfassung:

| Szenario | Muster |
|---|---|
| Synchrone Abfrage: „Existiert Haushalt X?" | **Named Interface** im Root-Package des Zielmoduls |
| Synchrone Abfrage: „Gib mir Aggregatdaten aus Modul X" | **Named Interface** |
| Reaktion auf Lifecycle-Event: „Haushalt wurde gelöscht" | **Domain Event** (`ApplicationEventPublisher` + `@ApplicationModuleListener`) |
| Reaktion auf Statusänderung: „Member wurde entfernt" | **Domain Event** |

**Bewusst abgelehnter Ansatz: lokale Datenkopien pro Modul (Event-Carried State Transfer).** Das wäre DDD-theoretisch reiner, erzeugt aber im Modular Monolith selbst-erzeugten Eventual-Consistency-Aufwand und unverhältnismäßigen Synchronisations-Overhead ohne echten Mehrwert. Die stärkere Kopplung durch Named Interfaces wird akzeptiert — sie ist explizit, minimal und von Spring Modulith verifizierbar.

**Modul-übergreifende DB-FKs sind verboten.** Jedes Modul hat ein eigenes PostgreSQL-Schema (`household`, `tasks`, `expenses`). Fremd-IDs werden als einfache UUIDs ohne DB-FK gespeichert.

**Event Publication Registry:** Die `event_publication`-Tabelle muss per Flyway-Migration angelegt werden — Spring Modulith würde sie sonst per Schema-Autogenerierung anlegen, was mit Flyway-only-Betrieb kollidiert.

### Pending Backend Implementations

- **Maximale Bildgröße**: `server.tomcat.max-http-post-size` ist bewusst auf unbegrenzt gesetzt (Tomcat-Standard war 2 MB und führte zu stiller Trunkierung großer Base64-Bilder). Eine serverseitige Validierung der maximalen Bildgröße (empfohlen: 5 MB) fehlt noch — per Bean-Validation oder Filter.
- **Bildtyp-Beschränkung**: Es wird noch nicht geprüft, ob ein hochgeladenes Bild ein valides JPEG/PNG/WebP ist. Magic-Bytes-Prüfung oder MIME-Detection fehlt.
- **Invite-Link-Erneuerung**: `POST /household/{householdId}/invite` (operationId: `generateInviteLink`) ist in der OpenAPI-Spec und im generierten Delegate vorhanden, aber in `HouseholdApiDelegateImpl` noch nicht implementiert (`NOT_IMPLEMENTED`). Benötigt: Token regenerieren, alten invalidieren, neue URL zurückgeben.