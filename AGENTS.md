# LibreHousehold Context for AI Assistants

This document is the canonical AI-assistant reference for LibreHousehold and should remain the single maintained source for project-specific assistant guidance.

## Project Overview

See [README.adoc](README.adoc) for the project description, goals, and feature list.
See [Arc42 Chapter 1](docs/architecture/modules/arc42/pages/01_introduction_and_goals.adoc) for the quality goals (QG1–QG4).

## Architecture Big Picture

LibreHousehold is a **Modular Monolith** designed for household management. It enforces strict separation between Frontend and Backend via an API-first approach.

- **Frontend:** SvelteKit 5 application (SPA/PWA)
- **API:** Defined in `api/openapi.yml`
- **Backend:** Java-based modular monolith (currently still WIP / mostly empty)
- **Documentation:** Arc42/AsciiDoc architecture documentation published as an Antora site (see [Documentation](#documentation))
- **External System:** E-Mail server for notifications

Architecture decisions are documented in [`docs/architecture/modules/adrs/pages/`](docs/architecture/modules/adrs/pages/).
System actors and module responsibilities are documented in [Arc42 Chapter 3](docs/architecture/modules/arc42/pages/03_context_and_scope.adoc) and [Arc42 Chapter 5](docs/architecture/modules/arc42/pages/05_building_block_view.adoc).

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
This starts `TestLibrehouseholdApplication`, which uses Testcontainers to automatically spin up PostgreSQL, Grafana LGTM, Mailpit and Dex. No manual DB setup required.

Dex is a local OIDC provider (`DexTestcontainersConfiguration`), registered as the social-login provider `dev-provider` via Spring Boot's standard properties (`spring.security.oauth2.client.registration.dev-provider.*`, `spring.security.oauth2.client.provider.dev-provider.issuer-uri`), so social login can be exercised without real Google/GitHub credentials:

- Test user: `dev@example.com` / `password`
- Client: `librehousehold-dev` / `librehousehold-dev-secret`
- Dex runs on a random port; its issuer is `http://localhost:<port>/dex`.
- The Testcontainers Dex module accepts only one redirect URI per client. The Dex client allows `http://localhost:5173/login/oauth2/code/dev-provider`, the Vite dev-proxy origin: the proxy forwards `/oauth2` and `/login/oauth2` (not `/login`, which is the SPA's login route) with `X-Forwarded-Host`, and the redirect URI is `{baseUrl}/login/oauth2/code/{registrationId}`, so the backend builds it from that origin. Log in through the frontend (`npm run dev` with `VITE_API_URL=http://localhost:8080/v1`). Calling the backend directly on port 8080 produces a redirect URI that Dex rejects.
- Integration tests run on `RANDOM_PORT`. Because of the one-redirect-URI limit, Dex ITs register the Dex client per test with the `@LocalServerPort` in its redirect URI.

`DexTestcontainersConfiguration` is deliberately separate from `TestcontainersConfiguration`: importing it enables social login for the whole application context, so only tests that need Dex import it.

Client registrations must not come only from a `DynamicPropertyRegistrar`: Spring Boot creates the `ClientRegistrationRepository` only if `spring.security.oauth2.client.registration.*` is already set when it evaluates its auto-configuration, which happens before registrar values exist. Without the repository, `oauth2Login()` is silently not configured and `/oauth2/authorization/{id}` just redirects to `/login`. The static registration values therefore live in `src/test/resources/dex-dev-provider.properties` (`@PropertySource`); only the Dex `issuer-uri` is registered dynamically.

For a plain start without containers (requires an external DB):
```bash
cd backend
./mvnw spring-boot:run
```

Until the first release, schema changes go directly into the existing Flyway migrations instead of new ones. An external dev DB therefore has to be dropped and recreated after such a change: Flyway refuses to start on the changed checksum, and stored data may have a different meaning (e.g. `account_identity.provider_id` holds the provider's issuer, no longer the registration ID). The Testcontainers start above always begins with a fresh DB.

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

**Validating the specs:**
Both specs are checked from `api/` (Node 24; `@asyncapi/cli` requires >= 24.11):
```bash
cd api
npm ci
npm run validate            # both of the following
npm run lint:openapi        # Spectral, ruleset api/.spectral.yaml (spectral:oas); warnings fail
npm run validate:asyncapi   # AsyncAPI CLI; even infos fail
```
The CI job `API` (`api-ci.yml`, called from `ci.yml`) runs both whenever `api/openapi.yml`, `api/asyncapi.yml` or the tooling changes.

**Realtime event stream (AsyncAPI):**
The SSE stream `GET /v1/household/{householdId}/events` (ADR-019) is described only in `api/asyncapi.yml`, not in `openapi.yml`. Change the event contract there first, then the code. No code is generated from the spec; payload types in frontend and backend are handwritten and reviewed against it. `validate:asyncapi` points `ASYNCAPI_METRICS_CONFIG_PATH` to the checked-in `api/asyncapi-analytics.json`, because the AsyncAPI CLI otherwise sends anonymous usage metrics on every local run and writes `~/.asyncapi-analytics` (it skips them only when `CI=true`). Run the CLI through `npm run`, not through a bare `npx asyncapi`.

**mokapi loads every file in `api/`** (`--Providers.File.Directory`). `api/.mokapiignore` keeps it to `openapi.yml` and `mokapi.js`: without it, mokapi executes scripts from `api/node_modules`, starts the servers of `asyncapi.yml` as Kafka brokers (one on its own port 8080) and crashes. Every new non-mock file in `api/` belongs in `.mokapiignore`.

### Testing & Quality

**Frontend (`frontend/`):**
- **Unit Tests:** `npm run test:unit` (Vitest). Run tests with `npm run test`
- **Coverage:** `npm run test:coverage` writes `coverage/lcov.info` (`@vitest/coverage-v8`), which the CI hands to SonarCloud. V8 coverage only works with Chromium in browser mode; other browsers would need `@vitest/coverage-istanbul`.
- **Linting:** `npm run lint` (ESLint + Prettier)
- **Formatting:** `npm run format`
- **Type Check:** `npm run check`
- **Translating:** Run `npm run paraglide` after updating/adding keys in `messages/de.json` or `messages/en.json`

**Backend (`backend/`):**
- **Tests:** `./mvnw verify` (JUnit 5, Testcontainers). **Not** `./mvnw test` — `*IT` classes run via
  `maven-failsafe-plugin`, bound to the `integration-test`/`verify` phases, which `test` never
  reaches. `./mvnw test` silently compiles but skips every `*IT` class; only `./mvnw verify` (or
  `./mvnw clean verify`) actually proves integration tests pass. See Findings & Notes below.
- **Format:** `./mvnw sortpom:sort` (for `pom.xml`)


## Development Context

### Project Structure

```text
LibreHousehold/
├── backend/           # Java backend (modular monolith, Spring Boot)
├── frontend/          # TypeScript PWA
├── docs/
│   ├── antora-playbook.yml  # Antora site configuration
│   ├── supplemental-ui/     # Antora UI overrides
│   └── architecture/        # Antora component "architecture"
│       └── modules/
│           ├── ROOT/        # Architecture overview (site start page)
│           ├── arc42/       # Arc42 chapters
│           └── adrs/        # Architecture Decision Records
├── build/             # Build artifacts (e.g. the documentation site in build/site)
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
- **i18n:** Paraglide (`#lib/paraglide/messages.js`)
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
  - `src/lib`: Shared components, utilities, and stores. Imported via the `#lib` subpath import from `package.json` (SvelteKit 3 no longer generates `$lib`); imports need the file extension, e.g. `#lib/stores/taskStore.ts`.
  - `src/lib/stores`: State management
  - `src/generated-sources`: **Do not edit manually**; generated API clients

### Documentation

- Architecture documentation lives in `docs/architecture/`
- Use AsciiDoc (`.adoc`) for architecture/project documentation
- The site is built with [Antora](https://antora.org) and deployed to GitHub Pages
  (`librehousehold.wiegandt.eu`) on every push to `main`. `docs-ci.yml` builds it in pull requests,
  so broken xrefs, includes and diagrams fail there.
- Build locally from `docs/` (output in `build/site`):
  ```bash
  cd docs
  npm ci
  npm run build:strict   # fails on every warning, like the CI
  ```
  PlantUML diagrams are rendered by Kroki. Locally the playbook uses `kroki.io`; with
  `docker run -p 8000:8000 yuzutech/kroki` and
  `npm run build:strict -- --attribute kroki-server-url=http://localhost:8000` the build runs
  offline, as in the CI.
- `asciidoctor-kroki` stays on 0.18.x: 1.x and later require `@asciidoctor/core` 4, but Antora 3.2
  ships core 2.2. npm installs both side by side without an error, and only the build fails
  (`parent.applySubs is not a function`). `dependabot.yml` therefore ignores `asciidoctor-kroki`
  `>=1.0.0`. When an Antora update brings `@asciidoctor/core` 4 (`npm ls @asciidoctor/core`), remove
  the ignore rule and update `asciidoctor-kroki` together with Antora.
- `personas/` and `domain-stories/` lie outside `modules/` and are deliberately not published.
- UI customizations of the Antora default UI live in `docs/supplemental-ui/`. Own CSS and JS
  (`css/librehousehold.css`, `js/image-zoom.js`) are included via `partials/head-meta.hbs`, which
  the default UI renders after `site.css`, so its rules win without specificity tricks. Kroki writes
  the height of inline SVGs (`opts=inline`) into their `style` attribute; only `!important`
  overrides it, otherwise scaled-down SVGs are distorted.

**Cross-references between modules.** Antora resolves `xref:` relative to the module's `pages/`
directory, not to the current file. Within a module, `xref:adr-016.adoc` is enough; across modules,
qualify the module: `xref:adrs:adr-011.adoc`, `xref:arc42:01_introduction_and_goals.adoc#QG2`.
Includes and diagrams use resource IDs: `include::arc42:page$03_context_and_scope.adoc[tag=…]`,
`plantuml::arc42:partial$system_context.puml[…]`.

**Content that is included into another module must be fully module-qualified.** Xrefs and resource
IDs in included content are resolved relative to the *including* page. The overview (module `ROOT`)
includes tagged regions of arc42 chapters 1, 3 and 4, and chapter 9 includes the ADR list from
`adrs/pages/index.adoc`; xrefs, includes and diagrams inside these regions therefore always carry
the module (`xref:arc42:…`, `xref:adrs:…`, `arc42:partial$…`).

**A new ADR has to be added in two places:** the list in `adrs/pages/index.adoc` (between the
`adr-list` tags, which chapter 9 includes) and the navigation `adrs/nav.adoc`. The template is
`adrs/partials/template.adoc`.

Curly braces in text are attribute references in AsciiDoc; escape literal placeholders such as
`` `/oauth2/authorization/\{registrationId}` ``, otherwise the strict build fails with "skipping
reference to missing attribute".

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

- **API Client:** Imported from `#lib/api/…` or generated sources
- **Mock Server:** Configured in `api/mokapi.js`. **`api/mokapi.js` is off-limits — do not modify it unless the user explicitly asks you to.**

## State Management Rules

- **During active frontend development, always go through the mock server (`api/mokapi.js`, the `npm run dev` default) or a real local backend (`VITE_API_URL=... npm run dev`) — never fake persistence locally instead.** E.g. don't implement "create a task" by only storing it in an in-memory/localStorage variable without an actual API call; build and test features against the real request/response cycle from the start.
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
- `docs/`: Architecture documentation (AsciiDoc, Antora)
- `build/`: Build artifacts (for example the documentation site in `build/site`)

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

- Validate OpenAPI changes before committing (`npm run lint:openapi` in `api/`).
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
- Every service method that reads from or writes to the database must have at least one happy-path integration test in a `*ServiceIT` class. Use `@DataJdbcTest` + Testcontainers (see `TaskServiceIT`, `ExpenseServiceIT` as reference). Repository-specific behaviour (e.g. cascades, derived queries) is verified there rather than in unit tests.
- Do not use `@BeforeEach` in integration tests. Declare all test data — including IDs and mock stubs — inline in the `// given` block of each test. Each test must be self-contained and readable without scrolling to a setup method. DRY is an anti-pattern in tests: duplicated setup is preferable to hidden shared state.

### Mapper-Tests (Java)

- MapStruct-Mapper sind interne Kollaborateure desselben Moduls und werden in Service-Tests **nie** gemockt.
- Stattdessen die echte Implementierung per `@Spy MemberMapper mapper = Mappers.getMapper(MemberMapper.class)` injizieren — funktioniert mit Mockito `@InjectMocks`.
- Mapper-Tests (`*MapperTest.java`) prüfen jede public Mapping-Methode in einer eigenen `@Nested`-Klasse. Kein Instancio in Mapper-Tests — Eingabedaten immer explizit konstruieren.
- For mapping methods that return a complex object: always create a single `expected` object in the `given` block and assert with `assertThat(result).usingRecursiveComparison().isEqualTo(expected)`. Never use multiple `assertThat(result.getX()).isEqualTo(...)` calls on a returned object.

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

### Auth integration tests: `RANDOM_PORT`, log in like the SPA does

Integration tests for login, logout, session and CSRF behaviour run the real server with `RANDOM_PORT` (not `MockMvc`), so they see real `Set-Cookie` attributes, the real session-ID change and real provider redirects. A local login in such a test goes through the same steps as the SPA: first a `GET` that issues the `XSRF-TOKEN` cookie (e.g. `GET /v1/auth/discover`), then `POST /v1/login` as form data with the field `email` (not `username`) plus `password`, and the token in the `X-XSRF-TOKEN` header. Every login and the logout issue a new `XSRF-TOKEN`; a follow-up request must use the new one.

### Publishing an event for an `@ApplicationModuleListener` requires an active transaction

`@ApplicationModuleListener` is an async `@TransactionalEventListener(AFTER_COMMIT)`. If `publishEvent` runs without an active transaction, Spring silently skips the listener, while Spring Modulith still records the publication in `event_publication`. Nothing fails; the mail is just never sent. This bug hit twice (`UnverifiedAccountExpiryJob` deletion warning, `resendVerificationEmail`). Every method that publishes such an event must therefore run inside a transaction: `@Transactional` called through the Spring proxy, or `TransactionOperations` for loops in non-transactional jobs. Its test must check the real effect, e.g. the mail arriving in Mailpit; a test of a timestamp or a recorded event does not catch the bug. Failed publications are resubmitted by `EventPublicationMaintenanceJob` (see Arc42 chapter 8, "Event Delivery and Resubmission").

### CI: `CI result` is the only required check

`.github/workflows/ci.yml` runs for every pull request and every push to any branch, without path filters. A `changes` job diffs the commit range (pull request: against its base; `main`: against the previous push; other branches: against the merge base with `main`) and calls `backend-ci.yml` / `frontend-ci.yml` (reusable, `workflow_call` only) when their area changed, including changes to the workflow files themselves. The `ci-result` job ("CI result") is the only check required by the `main` ruleset: it fails if any job in its `needs` failed or was cancelled, and passes if they were skipped.

Do not put path filters on a workflow whose check is required: a workflow that never starts leaves its check pending forever and blocks the merge (this blocked every GitHub Actions bump). A new workflow that has to pass before a merge must be called from `ci.yml` and added to `ci-result.needs`; otherwise it does not block a merge or Dependabot's auto-merge.

### `./mvnw test` runs without integration tests — use `./mvnw verify`

`backend/pom.xml` binds `maven-failsafe-plugin` with the `integration-test`+`verify` goals without
its own `<phase>` override, so it runs in their default phases — both come **after** `test` in the
Maven lifecycle. `maven-surefire-plugin` has no `<configuration>` of its own, so it uses its default
include pattern (`**/*Test.java` etc.), which excludes `*IT.java` classes. Consequence: `./mvnw test`
compiles every `*IT` class without complaint but executes **none** of them — a green `test` run
proves nothing about integration tests. Only `./mvnw verify` (or `./mvnw clean verify`) actually runs
both unit and integration tests. This gap silently hid a real regression during the auth
implementation (P1.4) — six broken IT classes in the `household` module, 33 test failures — across
several rounds, even though "all tests green" was reported repeatedly; those reports only ever
covered `test`, never `verify`.

### Spring Data JDBC Records mit client-generierten UUIDs

Entities als Records mit client-generierten UUIDs müssen `Persistable<UUID>` implementieren und `isNew() = true` zurückgeben, da Spring Data JDBC sonst anhand der nicht-null UUID ein UPDATE statt eines INSERT ausführt — das bei leerer Tabelle lautlos scheitert.

For entities implemented as classes with setters (not records), call `entity.markExisting()` before `save()` so Spring Data JDBC issues an UPDATE instead of INSERT. For record-based entities, use `@Modifying @Query` for updates since records are immutable:

```java
@Modifying
@Query("UPDATE household SET name = :name WHERE id = :id")
void updateName(@Param("id") UUID id, @Param("name") String name);
```

For class-based entities the recommended update pattern is via MapStruct `@MappingTarget` — see **Update Pattern (Spring Data JDBC + MapStruct)** below.

### Account & Email Model

**Account–Household relationship: resolved as 1 Account : 1 Household** (see [ADR-012](docs/architecture/modules/adrs/pages/adr-012.adoc)). An account is scoped to exactly one household; there is no cross-household login. Consequences:

- **Email uniqueness scope**: `member.email` staying globally unique (system-wide UNIQUE constraint) is the correct, natural fit for the 1:1 model — one e-mail identifies exactly one account/household. No per-household uniqueness needed.
- **Existing accounts**: Resolved by the 1:1 model — an account can never join or set up a second household. The setup wizard and registration flow must reject an e-mail that already has an account with a clear error, not a generic 409.
- **Account registration stays in `household`**: Since an account is just an attribute of a membership (not an independent, cross-household entity), `AccountRegistration`/`MemberManagementService` in the `household` module remains the correct place for it — no separate Identity/Account module is needed.
- **Email availability check**: `GET /members/availability` lets a client check whether a given e-mail address is already registered before household setup/invite-join, avoiding a generic 409 with no actionable information. Implemented via `MemberManagementService.isEmailAvailable`, not a separate account-scoped endpoint — "does a member with this email exist" is a `member`/`household` fact, independent of authentication method.

### Task Schema

The `Task` schema has a `done` field (date format) that either contains a date or is `null`. Clients must treat a missing `done` the same as `null`: the backend generates it as `JsonNullable` with `NON_ABSENT`, so responses that never set it (e.g. `createTask`) omit the field entirely.

### Recurring Tasks

- `recurring: boolean`
- `recurrenceUnit`: days, weeks, months, years
- `recurrenceInterval`: count (minimum `1`)
- For recurring tasks, the task is considered done only if the done date is after the last due date.

### Cross-Module Communication (ADR-011)

Entschieden in [ADR-011](docs/architecture/modules/adrs/pages/adr-011.adoc). Kurzfassung:

| Szenario | Muster |
|---|---|
| Synchrone Abfrage: „Existiert Haushalt X?" | **Named Interface** im Root-Package des Zielmoduls |
| Synchrone Abfrage: „Gib mir Aggregatdaten aus Modul X" | **Named Interface** |
| Reaktion auf Lifecycle-Event: „Haushalt wurde gelöscht" | **Domain Event** (`ApplicationEventPublisher` + `@ApplicationModuleListener`) |
| Reaktion auf Statusänderung: „Member wurde entfernt" | **Domain Event** |

**Bewusst abgelehnter Ansatz: lokale Datenkopien pro Modul (Event-Carried State Transfer).** Das wäre DDD-theoretisch reiner, erzeugt aber im Modular Monolith selbst-erzeugten Eventual-Consistency-Aufwand und unverhältnismäßigen Synchronisations-Overhead ohne echten Mehrwert. Die stärkere Kopplung durch Named Interfaces wird akzeptiert — sie ist explizit, minimal und von Spring Modulith verifizierbar.

**Modul-übergreifende DB-FKs sind verboten.** Jedes Modul hat ein eigenes PostgreSQL-Schema (`household`, `tasks`, `expenses`). Fremd-IDs werden als einfache UUIDs ohne DB-FK gespeichert.

**Event Publication Registry:** Die `event_publication`-Tabelle muss per Flyway-Migration angelegt werden — Spring Modulith würde sie sonst per Schema-Autogenerierung anlegen, was mit Flyway-only-Betrieb kollidiert.

### Update Pattern (Spring Data JDBC + MapStruct)

For partial updates (PATCH), use a MapStruct method annotated with `@MappingTarget` together with `repository.save()`. The entity's `markExisting()` callback (triggered by `@BeforeSave`) sets `isNew = false` so Spring Data JDBC issues an UPDATE instead of INSERT. `@MappedCollection` children (e.g. `expense_split`) are automatically cascaded during `save()`.

```java
@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
@Mapping(target = "fieldName", source = "fieldName",
         conditionQualifiedByName = "isPresent", qualifiedByName = "fromOptionalString")
void updateEntityFromUpdate(SomeUpdate source, @MappingTarget SomeEntity target);
```

Use `@Condition @Named("isPresent")` from `CoreOptionalMapper` to skip Optional fields that are empty, preventing existing values from being overwritten when a field is absent from the request. `CoreOptionalMapper` (`eu.wiegandt.librehousehold.core`) provides shared Optional conversion qualifiers; all mappers that need Optional handling should implement this interface.

Examples: `TaskMapper.updateEntityFromEdit`, `ExpenseMapper.updateEntityFromUpdate`, `ReimbursementMapper.updateEntityFromUpdate`

### Spring Data JDBC: Prefer Derived Queries

Spring Data JDBC automatically derives methods such as `findByHouseholdId`, `deleteByHouseholdId`, and `existsByHouseholdIdAndName` — no `@Query` annotation needed. Use `@Query` only for complex queries that cannot be derived (e.g. multi-table joins, subqueries).

**Important:** Derived `deleteBy*` methods execute raw SQL and do NOT cascade `@MappedCollection` children. Use `deleteAll(findByX(...))` for cascade-correct bulk deletes (loads entities first, then deletes each with full cascade).

### Email Verification (P2.2): `household` -> `notifications` Module Boundary

`notifications.internal` (e.g. `EmailSenderService`) must never be called directly from `household` — not even from a non-controller context like a `@Scheduled` job. `household` publishes a domain event (`AccountRegistered`, `VerificationEmailRequested`, `VerificationDeletionWarningRequested`, all in the `household` root package) and `notifications.internal.AccountRegistrationListener` reacts via `@ApplicationModuleListener`. A first implementation of `UnverifiedAccountExpiryJob` called `EmailSenderService` directly from `household.service` and was only caught by `ApplicationTests.writeDocumentationSnippets` (`ApplicationModules.verify()`), which fails with a "Cycle detected: Slice household -> Slice notifications -> Slice household" error, since `notifications` already depends on `household`'s public API (`AccountTokenIssuer`) in the other direction. Any new email-triggering behavior in `household` needs its own event, published via the already-injected `ApplicationEventPublisher`, never a direct call into `notifications`.

### Testing `JavaMailSender`: Mock the Concrete `JavaMailSenderImpl`, Not the Interface

`@MockitoBean private JavaMailSender mailSender;` breaks full-context tests (`@SpringBootTest`) with `IllegalArgumentException: 'beans' must not be empty` from `MailHealthContributorAutoConfiguration`: Boot's mail health indicator looks up beans by the concrete `JavaMailSenderImpl` type specifically (`@ConditionalOnBean(JavaMailSenderImpl.class)` / `getBeansOfType(JavaMailSenderImpl.class)`), and a mock declared against the `JavaMailSender` interface doesn't satisfy that lookup. Use `@MockitoBean private JavaMailSenderImpl mailSender;` instead (Mockito's CGLIB proxy for a concrete class still satisfies `instanceof JavaMailSenderImpl`); the `JavaMailSender`-typed constructor parameter elsewhere still resolves to the same mock, since the interface is implemented by that class.

### `AccountSessionSignIn` Bypasses `isEnabled` on Purpose and Stays Module-Internal

`household.service.AccountSessionSignIn.signIn(memberId)` establishes the first session right after household setup or invite join. It deliberately does **not** go through the `AuthenticationManager`/`DaoAuthenticationProvider`: that pipeline enforces `UserDetails.isEnabled()`, which would reject this session, since a freshly registered account is always unverified. It loads the principal via `AccountUserDetailsService.loadUserByMemberId` (no lock or verification check) and applies the `@Primary` `SessionAuthenticationStrategy` (session-ID change, session registration, new CSRF token).

Two rules keep this credential-less sign-in safe:

- **Only after the commit.** It is called only from `HouseholdApiDelegateImpl.setupHousehold` and `MembersApiDelegateImpl.joinHousehold`, after the `@Transactional` service call has returned. Never call it from inside the service: if the transaction rolls back, a session for a non-existent member would remain.
- **Never cross-module.** It is internal to `household`. The Named Interface `household.AccountSignIn` exposes only `signInByExternalIdentity(providerId, externalSubject)`, which `config` uses after the social-login callback. Do not add a `signIn(memberId)` to the Named Interface: any module could then sign in any member without a credential.

### Pending Backend Implementations

See [Chapter 11 - Risks and Technical Debts](docs/architecture/modules/arc42/pages/11_technical_risks.adoc) for the full, prioritized list (TD1-TD4). Summary of the two still-open items:

- **Maximale Bildgröße** (TD2): `server.tomcat.max-http-post-size` ist bewusst auf unbegrenzt gesetzt (Tomcat-Standard war 2 MB und führte zu stiller Trunkierung großer Base64-Bilder). Eine serverseitige Validierung der maximalen Bildgröße (empfohlen: 5 MB) fehlt noch — per Bean-Validation oder Filter.
- **Bildtyp-Beschränkung** (TD3): Es wird noch nicht geprüft, ob ein hochgeladenes Bild ein valides JPEG/PNG/WebP ist. Magic-Bytes-Prüfung oder MIME-Detection fehlt.